package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public class CrptApi {
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final Semaphore rateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        rateLimiter = new Semaphore(requestLimit);
    }

    // Основной метод для тестирования
    public static void main(String[] args) {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 5); // Ограничение: 5 запросов в секунду
        Document document = new Document();
        // Заполнение документа данными
        boolean result = api.createDocument(document, "signature");
        System.out.println("Результат запроса: " + result);
    }

    public boolean createDocument(Document document, String signature) {
        try {
            if (!rateLimiter.tryAcquire(1, TimeUnit.SECONDS)) {
                System.out.println("Превышено ограничение на количество запросов");
                return false;
            }
            String jsonData = objectMapper.writeValueAsString(document);
            return sendPostRequest(jsonData);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            System.out.println("Ошибка при обработке запроса: " + e.getMessage());
            return false;
        } finally {
            rateLimiter.release();
        }
    }

    private boolean sendPostRequest(String jsonData) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(CrptApi.API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonData.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            StringBuilder response = new StringBuilder();
            extracted(connection, response);

            System.out.println("Ответ от сервера: " + response);
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            System.out.println("Ошибка при отправке запроса: " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void extracted(HttpURLConnection conn, StringBuilder response) throws IOException {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }
    }

    // Внутренние классы для представлений
    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Document {

        public Description description;
        public String docId;
        public String docStatus;
        public String docType;
        public boolean importRequest;
        public String ownerInn;
        public String participantInn;
        public String producerInn;
        public LocalDateTime productionDate;
        public String productionType;
        public List<Product> products;
        public LocalDateTime regDate;
        public String regNumber;

    }

    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Description {
        public String participantInn;
    }

    @Data
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Product {
        public String certificateDocument;
        public LocalDateTime certificateDocumentDate;
        public String certificateDocumentNumber;
        public String ownerInn;
        public String producerInn;
        public LocalDateTime productionDate;
        public String tnvedCode;
        public String uitCode;
        public String uituCode;
    }

}