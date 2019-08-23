package com.intellij.jps.cache.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static com.intellij.jps.cache.client.ArtifactoryQueryBuilder.Name;
import static com.intellij.jps.cache.client.ArtifactoryQueryBuilder.Sort;

public class ArtifactoryJpsCacheServerClient implements JpsCacheServerClient {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.client.ArtifactoryJpsCacheServerClient");
  private static final String AUTH_TOKEN = "";
  private static final String ARTIFACTORY_API_URL = "https://repo.labs.intellij.net/api/search/aql";
  private static final String REPOSITORY_NAME = "intellij-jps-compilation-caches";
  private static final String AUTH_HEADER_NAME = "X-JFrog-Art-Api";
  private static final String CONTENT_TYPE = "text/plain";
  private static final ObjectMapper jackson = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @NotNull
  @Override
  public Set<String> getAllCacheKeys() {
    String searchQuery = new ArtifactoryQueryBuilder()
      .findRepository(Name.eq(REPOSITORY_NAME))
      .withPath(Name.match("caches"))
      .sortBy(Sort.desc("created"))
      .build();
    ArtifactoryEntryDto[] responseDtos = doPostRequest(searchQuery, ArtifactoryEntryDto[].class);
    if (responseDtos == null) return Collections.emptySet();
    return Arrays.stream(responseDtos).map(ArtifactoryEntryDto::getName).collect(Collectors.toSet());
  }

  @NotNull
  @Override
  public Set<String> getAllBinaryKeys() {
    String searchQuery = new ArtifactoryQueryBuilder()
      .findRepository(Name.eq(REPOSITORY_NAME))
      .withPath(Name.match("binaries"))
      .sortBy(Sort.desc("created"))
      .build();
    ArtifactoryEntryDto[] responseDtos = doPostRequest(searchQuery, ArtifactoryEntryDto[].class);
    if (responseDtos == null) return Collections.emptySet();
    return Arrays.stream(responseDtos).map(ArtifactoryEntryDto::getName).collect(Collectors.toSet());
  }

  private static <T> T doPostRequest(String searchQuery, Class<T> responseClass) {
    try {
      HttpRequests.post(ARTIFACTORY_API_URL, CONTENT_TYPE)
        .tuner(connection -> connection.addRequestProperty(AUTH_HEADER_NAME, AUTH_TOKEN))
        .connect(it -> {
          it.write(searchQuery);

          URLConnection connection = it.getConnection();
          if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection)connection;
            if (httpConnection.getResponseCode() == 200) {
              InputStream inputStream = httpConnection.getInputStream();
              if (httpConnection.getContentEncoding().equals("gzip")) {
                try (InputStream gzipInputStream = new GZIPInputStream(inputStream)) {
                  return parseResponse(gzipInputStream, responseClass);
                }
              }
              return parseResponse(inputStream, responseClass);
            }
            else {
              String statusLine = httpConnection.getResponseCode() + " " + httpConnection.getRequestMethod();
              String errorText = getErrorText(httpConnection);
              LOG.debug("Request: " + httpConnection.getRequestMethod() + httpConnection.getURL() + " : Error " + statusLine + " body: " +
                        errorText);
            }
          }
          return null;
        });
    }
    catch (IOException e) {
      LOG.warn("Failed request to cache artifactory", e);
    }
    return null;
  }

  private static <T> T parseResponse(InputStream response, Class<T> responseClass) throws IOException {
    JsonNode jsonNode = jackson.readTree(response).findValue("results");
    return jackson.treeToValue(jsonNode, responseClass);
  }

  private static String getErrorText(HttpURLConnection connection) throws IOException {
    InputStream errorStream = connection.getErrorStream();
    if (connection.getContentEncoding() == "gzip") {
      try (InputStream gzipInputStream = new GZIPInputStream(errorStream)) {
        return StreamUtil.readText(gzipInputStream, StandardCharsets.UTF_8);
      }
    }
    else {
      return StreamUtil.readText(errorStream, StandardCharsets.UTF_8);
    }
  }
}
