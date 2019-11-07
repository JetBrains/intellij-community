package com.intellij.jps.cache.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.jps.cache.JpsCachesUtils;
import com.intellij.jps.cache.model.AffectedModule;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static com.intellij.jps.cache.client.ArtifactoryQueryBuilder.Name;
import static com.intellij.jps.cache.client.ArtifactoryQueryBuilder.Sort;

public class ArtifactoryJpsServerClient implements JpsServerClient {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.client.ArtifactoryJpsCacheServerClient");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  public static final ArtifactoryJpsServerClient INSTANCE = new ArtifactoryJpsServerClient();
  private static final String REPOSITORY_NAME = "intellij-jps-compilation-caches";
  private static final String CONTENT_TYPE = "text/plain";
  private final String stringOne;
  private final String stringTwo;
  private final String stringThree;

  private ArtifactoryJpsServerClient() {
    byte[] decodedBytes = Base64.getDecoder().decode("WC1KRnJvZy1BcnQtQXBp");
    stringOne = new String(decodedBytes, CharsetToolkit.UTF8_CHARSET);
    decodedBytes = Base64.getDecoder().decode("QUtDcDVkTDM5TkJyUXY4ZTlQWDNtUXRHMnBRdDJ4WlZnVVRrWk5jQXFWYVRWUmdqQ3NzRFlSaEFwVW0zdUdON3VmdjN6ZnZFOA==");
    stringTwo = new String(decodedBytes, CharsetToolkit.UTF8_CHARSET);
    decodedBytes = Base64.getDecoder().decode("aHR0cHM6Ly9yZXBvLmxhYnMuaW50ZWxsaWoubmV0Lw==");
    stringThree = new String(decodedBytes, CharsetToolkit.UTF8_CHARSET);
  }

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

  @Nullable
  @Override
  public File downloadMetadataById(@NotNull String metadataId, @NotNull File targetDir) {
    String downloadUrl = stringThree + REPOSITORY_NAME + "/metadata/" + metadataId;
    DownloadableFileService service = DownloadableFileService.getInstance();
    String fileName = "metadata.json";
    DownloadableFileDescription description = service.createFileDescription(downloadUrl, fileName);
    FileDownloader downloader = service.createDownloader(Collections.singletonList(description), fileName);

    LOG.debug("Downloading JPS metadata from: " + downloadUrl);
    File metadataFile = null;
    try {
      List<Pair<File, DownloadableFileDescription>> pairs = downloader.download(targetDir);
      Pair<File, DownloadableFileDescription> first = ContainerUtil.getFirstItem(pairs);
      metadataFile = first != null ? first.first : null;
      if (metadataFile == null) {
        LOG.warn("Failed to download JPS metadata");
        return null;
      }
      return metadataFile;
    }
    catch (ProcessCanceledException | IOException e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof IOException) LOG.warn("Failed to download JPS metadata from URL: " + downloadUrl, e);
      if (metadataFile != null && metadataFile.exists()) {
        FileUtil.delete(metadataFile);
      }
      return null;
    }
  }

  @Override
  public Pair<Boolean, File> downloadCacheById(@NotNull SegmentedProgressIndicatorManager indicatorManager, @NotNull String cacheId,
                                               @NotNull File targetDir) {
    String downloadUrl = stringThree + REPOSITORY_NAME + "/caches/" + cacheId;
    String fileName = "portable-build-cache.zip";
    File tmpFolder = new File(targetDir, "tmp");
    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription description = service.createFileDescription(downloadUrl, fileName);
    JpsOutputsDownloader outputsDownloader = new JpsOutputsDownloader(Collections.singletonList(description), indicatorManager);

    LOG.debug("Downloading JPS caches from: " + downloadUrl);
    File zipFile = null;
    try {
      ProgressIndicator indicator = indicatorManager.getProgressIndicator();
      List<Pair<File, DownloadableFileDescription>> pairs = outputsDownloader.download(targetDir);
      indicator.checkCanceled();
      indicatorManager.setText(this, "Extracting downloaded results...");
      Pair<File, DownloadableFileDescription> first = ContainerUtil.getFirstItem(pairs);
      zipFile = first != null ? first.first : null;
      if (zipFile == null) {
        LOG.warn("Failed to download JPS caches");
        return new Pair<>(false, tmpFolder);
      }
      ZipUtil.extract(zipFile, tmpFolder, null);
      FileUtil.delete(zipFile);
      indicatorManager.finished(this);
      return new Pair<>(true, tmpFolder);
    }
    catch (ProcessCanceledException | IOException e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof IOException) LOG.warn("Failed to download JPS caches from URL: " + downloadUrl, e);
      if (zipFile != null && zipFile.exists()) {
        FileUtil.delete(zipFile);
      }
      FileUtil.delete(tmpFolder);
      return new Pair<>(false, tmpFolder);
    }
  }

  @Override
  public Pair<Boolean, Map<File, String>> downloadCompiledModules(@NotNull SegmentedProgressIndicatorManager indicatorManager,
                                                                  @NotNull List<AffectedModule> affectedModules) {
    File targetDir = new File(PathManager.getPluginTempPath(), JpsCachesUtils.PLUGIN_NAME);
    if (!targetDir.exists()) targetDir.mkdirs();

    Map<String, AffectedModule> urlToModuleNameMap = affectedModules.stream().collect(Collectors.toMap(
                            module -> stringThree + REPOSITORY_NAME + "/" + module.getType() + "/" + module.getName() + "/" + module.getHash(),
                            module -> module));

    DownloadableFileService service = DownloadableFileService.getInstance();
    List<DownloadableFileDescription> descriptions = ContainerUtil.map(urlToModuleNameMap.entrySet(),
                                                                       entry -> service.createFileDescription(entry.getKey(),
                                                                       entry.getValue().getOutPath().getName() + ".zip"));
    JpsOutputsDownloader outputsDownloader = new JpsOutputsDownloader(descriptions, indicatorManager);

    Map<File, String> result = new HashMap<>();
    List<File> downloadedFiles = new ArrayList<>();
    try {
      ProgressIndicator indicator = indicatorManager.getProgressIndicator();
      List<Pair<File, DownloadableFileDescription>> download = outputsDownloader.download(targetDir);
      downloadedFiles = ContainerUtil.map(download, pair -> pair.first);
      indicatorManager.setText(this, "Extracting downloaded results...");
      for (Pair<File, DownloadableFileDescription> pair : download) {
        indicator.checkCanceled();
        File zipFile = pair.first;
        String downloadUrl = pair.second.getDownloadUrl();
        AffectedModule affectedModule = urlToModuleNameMap.get(downloadUrl);
        File outPath = affectedModule.getOutPath();
        LOG.info("Downloaded JPS compiled module from: " + downloadUrl);
        File tmpFolder = new File(outPath.getParent(), outPath.getName() + "_tmp");
        ZipUtil.extract(zipFile, tmpFolder, null);
        FileUtil.delete(zipFile);
        result.put(tmpFolder, affectedModule.getName());
      }
      indicatorManager.finished(this);
      return new Pair<>(true, result);
    }
    catch (ProcessCanceledException | IOException e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof IOException) LOG.warn("Failed to download JPS compilation outputs", e);
      result.forEach((key, value) -> FileUtil.delete(key));
      downloadedFiles.forEach(zipFile -> FileUtil.delete(zipFile));
      return new Pair<>(false, Collections.emptyMap());
    }
  }

  @Override
  public void uploadBinaryData(@NotNull File uploadData, @NotNull String moduleName, @NotNull String prefix) {
    String uploadUrl = stringThree + REPOSITORY_NAME + "/binaries/" + moduleName + "/" + prefix + "/";
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

  private <T> T doPostRequest(String searchQuery, Class<T> responseClass) {
    try {
      return HttpRequests.post(stringThree + "api/search/aql", CONTENT_TYPE)
        .tuner(connection -> connection.addRequestProperty(stringOne, stringTwo))
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