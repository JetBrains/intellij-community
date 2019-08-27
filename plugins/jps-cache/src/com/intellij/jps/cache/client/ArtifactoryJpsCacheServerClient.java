package com.intellij.jps.cache.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static com.intellij.jps.cache.client.ArtifactoryQueryBuilder.Name;
import static com.intellij.jps.cache.client.ArtifactoryQueryBuilder.Sort;

public class ArtifactoryJpsCacheServerClient implements JpsCacheServerClient {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.client.ArtifactoryJpsCacheServerClient");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String AUTH_TOKEN = "";
  private static final String ARTIFACTORY_URL = "https://repo.labs.intellij.net/";
  private static final String REPOSITORY_NAME = "intellij-jps-compilation-caches";
  private static final String AUTH_HEADER_NAME = "X-JFrog-Art-Api";
  private static final String CONTENT_TYPE = "text/plain";

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

  @Override
  public void downloadCacheByIdAsynchronously(@NotNull Project project, @NotNull String cacheId, @NotNull File targetDir,
                                              @NotNull Consumer<File> callbackOnSuccess) {
    String downloadUrl = ARTIFACTORY_URL + REPOSITORY_NAME + "/caches/" + cacheId;
    String fileName = "portable-build-cache.zip";
    LOG.debug("Downloading JPS build caches from: " + downloadUrl);
    downloadArchive(project, downloadUrl, targetDir, fileName, "tmp", callbackOnSuccess);
  }

  @Override
  public void downloadCompiledModuleByNameAndHash(@NotNull Project project, @NotNull String moduleName, @NotNull String prefix,
                                                  @NotNull String moduleHash, @NotNull File targetDir, @NotNull BiConsumer<File, String> callbackOnSuccess) {
    String downloadUrl = ARTIFACTORY_URL + REPOSITORY_NAME + "/binaries/" + moduleName + "/" + prefix + "/" + moduleHash;
    String fileName = moduleName + ".zip";
    LOG.debug("Downloading JPS compiled module from: " + downloadUrl);
    downloadArchive(project, downloadUrl, targetDir, fileName, moduleName + "_tmp", file -> callbackOnSuccess.accept(file,moduleName));
  }

  @Override
  public void uploadBinaryData(@NotNull File uploadData, @NotNull String moduleName, @NotNull String prefix) {
    String uploadUrl = ARTIFACTORY_URL + REPOSITORY_NAME + "/binaries/" + moduleName + "/" + prefix + "/";
    try {
      //TODO :: Rewrite from cURL to REST
      ProcessOutput processOutput = ExecUtil.execAndGetOutput(new GeneralCommandLine()
                                            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                                            .withExePath("/usr/bin/curl")
                                            .withParameters(Arrays.asList("-T", uploadData.getAbsolutePath(), uploadUrl)));
      if (processOutput.getExitCode() != 0) {
        LOG.warn("Couldn't upload binary data: " + uploadUrl + " " + processOutput.getStderr());
      }
    }
    catch (ExecutionException e) {
      LOG.warn("Couldn't upload binary data: " + uploadUrl, e);
    }
  }

  private static void downloadArchive(@NotNull Project project, @NotNull String downloadUrl, @NotNull File targetDir, @NotNull String fileName,
                                      @NotNull String tmpFolderName, @NotNull Consumer<File> callbackOnSuccess) {
    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription description = service.createFileDescription(downloadUrl, fileName);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), fileName);

    Task.Backgroundable task = new Task.Backgroundable(project, "Download JPS Caches") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<Pair<File, DownloadableFileDescription>> pairs = downloader.download(targetDir);
          Pair<File, DownloadableFileDescription> first = ContainerUtil.getFirstItem(pairs);
          File file = first != null ? first.first : null;
          if (file == null) {
            callbackOnSuccess.accept(null);
            return;
          }
          File tmpFolder = new File(targetDir, tmpFolderName);
          ZipUtil.extract(file, tmpFolder, null);
          FileUtil.delete(file);
          callbackOnSuccess.accept(tmpFolder);
        }
        catch (IOException e) {
          LOG.warn("Failed to download JPS caches from URL: " + downloadUrl, e);
        }
      }
    };
    BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(task);
    processIndicator.setIndeterminate(false);
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator);
  }

  private static <T> T doPostRequest(String searchQuery, Class<T> responseClass) {
    try {
      return HttpRequests.post(ARTIFACTORY_URL + "api/search/aql", CONTENT_TYPE)
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
    JsonNode jsonNode = OBJECT_MAPPER.readTree(response).findValue("results");
    return OBJECT_MAPPER.treeToValue(jsonNode, responseClass);
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
