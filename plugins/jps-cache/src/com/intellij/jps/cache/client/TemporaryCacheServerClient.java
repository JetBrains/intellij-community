package com.intellij.jps.cache.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.jps.cache.JpsCachesPluginUtil;
import com.intellij.jps.cache.model.AffectedModule;
import com.intellij.jps.cache.model.OutputLoadResult;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.FileDownloader;
import com.intellij.util.io.HttpRequests;
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

public class TemporaryCacheServerClient implements JpsServerClient {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.client.TemporaryCacheServerClient");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  static final TemporaryCacheServerClient INSTANCE = new TemporaryCacheServerClient();
  private static final String REPOSITORY_NAME = "jps/intellij";
  private final String stringThree;

  private TemporaryCacheServerClient() {
    byte[] decodedBytes = Base64.getDecoder().decode("aHR0cHM6Ly90ZW1wb3JhcnktZmlsZXMtY2FjaGUubGFicy5qYi5nZy9jYWNoZS8=");
    stringThree = new String(decodedBytes, CharsetToolkit.UTF8_CHARSET);
  }

  @NotNull
  @Override
  public Set<String> getAllCacheKeys() {
    Map<String, List<String>> response = doGetRequest();
    if (response == null) return Collections.emptySet();
    return response.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());
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
    File metadataFile;
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
      return null;
    }
  }

  @Nullable
  @Override
  public File downloadCacheById(@NotNull SegmentedProgressIndicatorManager downloadIndicatorManager, @NotNull String cacheId,
                                @NotNull File targetDir) {
    String downloadUrl = stringThree + REPOSITORY_NAME + "/caches/" + cacheId;
    String fileName = "portable-build-cache.zip";
    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription description = service.createFileDescription(downloadUrl, fileName);
    JpsOutputsDownloader outputsDownloader = new JpsOutputsDownloader(Collections.singletonList(description), downloadIndicatorManager);

    LOG.debug("Downloading JPS caches from: " + downloadUrl);
    File zipFile;
    try {
      List<Pair<File, DownloadableFileDescription>> pairs = outputsDownloader.download(targetDir);
      downloadIndicatorManager.finished(this);

      Pair<File, DownloadableFileDescription> first = ContainerUtil.getFirstItem(pairs);
      zipFile = first != null ? first.first : null;
      if (zipFile != null) return zipFile;
      LOG.warn("Failed to download JPS caches");
    }
    catch (ProcessCanceledException | IOException e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof IOException) LOG.warn("Failed to download JPS caches from URL: " + downloadUrl, e);
    }
    return null;
  }

  @Override
  public List<OutputLoadResult> downloadCompiledModules(@NotNull SegmentedProgressIndicatorManager downloadIndicatorManager,
                                                        @NotNull List<AffectedModule> affectedModules) {
    File targetDir = new File(PathManager.getPluginTempPath(), JpsCachesPluginUtil.PLUGIN_NAME);
    if (targetDir.exists()) FileUtil.delete(targetDir);
    targetDir.mkdirs();

    Map<String, AffectedModule> urlToModuleNameMap = affectedModules.stream().collect(Collectors.toMap(
                            module -> stringThree + REPOSITORY_NAME + "/" + module.getType() + "/" + module.getName() + "/" + module.getHash(),
                            module -> module));

    DownloadableFileService service = DownloadableFileService.getInstance();
    List<DownloadableFileDescription> descriptions = ContainerUtil.map(urlToModuleNameMap.entrySet(),
                                                                       entry -> service.createFileDescription(entry.getKey(),
                                                                       entry.getValue().getOutPath().getName() + ".zip"));
    JpsOutputsDownloader outputsDownloader = new JpsOutputsDownloader(descriptions, downloadIndicatorManager);

    List<File> downloadedFiles = new ArrayList<>();
    try {
      // Downloading process
      List<Pair<File, DownloadableFileDescription>> download = outputsDownloader.download(targetDir);
      downloadIndicatorManager.finished(this);

      downloadedFiles = ContainerUtil.map(download, pair -> pair.first);
      return ContainerUtil.map(download, pair -> {
        String downloadUrl = pair.second.getDownloadUrl();
        return new OutputLoadResult(pair.first, downloadUrl, urlToModuleNameMap.get(downloadUrl));
      });
    }
    catch (ProcessCanceledException | IOException e) {
      //noinspection InstanceofCatchParameter
      if (e instanceof IOException) LOG.warn("Failed to download JPS compilation outputs", e);
      if (targetDir.exists()) FileUtil.delete(targetDir);
      downloadedFiles.forEach(zipFile -> FileUtil.delete(zipFile));
      return null;
    }
  }

  private Map<String, List<String>> doGetRequest() {
    try {
      return HttpRequests.request(stringThree + REPOSITORY_NAME + "/commit_history.json")
        .connect(it -> {
          URLConnection connection = it.getConnection();
          if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection)connection;
            if (httpConnection.getResponseCode() == 200) {
              InputStream inputStream = httpConnection.getInputStream();
              GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
              return OBJECT_MAPPER.readValue(gzipInputStream, new TypeReference<Map<String, List<String>>>() {});
            }

            else {
              String statusLine = httpConnection.getResponseCode() + " " + httpConnection.getRequestMethod();
              InputStream errorStream = httpConnection.getErrorStream();
              String errorText = StreamUtil.readText(errorStream, StandardCharsets.UTF_8);
              LOG.debug("Request: " + httpConnection.getRequestMethod() + httpConnection.getURL() + " : Error " + statusLine + " body: " +
                        errorText);
            }
          }
          return null;
        });
    }
    catch (IOException e) {
      LOG.warn("Failed request to cache server", e);
    }
    return null;
  }
}