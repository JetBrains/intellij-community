package com.intellij.jps.cache.client;

import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.io.HttpRequests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static com.intellij.jps.cache.client.ArtifactoryQueryBuilder.Name;
import static com.intellij.jps.cache.client.ArtifactoryQueryBuilder.Sort;

public class ArtifactoryJpsCacheServerClient implements JpsCacheServerClient {
  private static final String ARTIFACTORY_API_URL = "https://repo.labs.intellij.net/api/search/aql";
  private static final String AUTH_HEADER_NAME = "X-JFrog-Art-Api";
  private static final String AUTH_TOKEN = "";
  public static final String CONTENT_TYPE = "text/plain";
  private static final String REPOSITORY_NAME = "intellij-jps-compilation-caches";

  @Override
  public Set<String> getAllCacheKeys() throws IOException {
    String query = new ArtifactoryQueryBuilder()
      .findRepository(Name.eq(REPOSITORY_NAME))
      .withPath(Name.match("caches"))
      .sortBy(Sort.desc("name"))
      .build();
    System.out.println(query);
    String connect = HttpRequests.post(ARTIFACTORY_API_URL, CONTENT_TYPE)
        .tuner(connection -> connection.addRequestProperty(AUTH_HEADER_NAME, AUTH_TOKEN))
        .connect(processor -> {
          processor.write(query);
          return StreamUtil.readText(processor.getInputStream(), StandardCharsets.UTF_8);
        });
    System.out.println(connect);
    return null;
  }

  @Override
  public Set<String> getAllBinaryKeys() throws IOException {
    String query = new ArtifactoryQueryBuilder()
      .findRepository(Name.eq(REPOSITORY_NAME))
      .withPath(Name.match("binaries"))
      .sortBy(Sort.desc("name"))
      .build();
    System.out.println(query);
    String connect = HttpRequests.post(ARTIFACTORY_API_URL, CONTENT_TYPE)
      .tuner(connection -> connection.addRequestProperty(AUTH_HEADER_NAME, AUTH_TOKEN))
      .connect(processor -> {
        processor.write(query);
        return StreamUtil.readText(processor.getInputStream(), StandardCharsets.UTF_8);
      });
    System.out.println(connect);
    return null;
  }
}
