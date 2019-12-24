package com.intellij.jps.cache.client;

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

public class TemporaryCacheServerClient implements JpsServerClient {
  private static final Logger LOG = Logger.getInstance("com.intellij.jps.cache.client.TemporaryCacheServerClient");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  static final TemporaryCacheServerClient INSTANCE = new TemporaryCacheServerClient();
  private static final String REPOSITORY_NAME = "jps/intellij";
  private final String stringThree;

  private TemporaryCacheServerClient() {
    byte[] decodedBytes = Base64.getDecoder().decode("aHR0cHM6Ly90ZW1wb3JhcnktY2FjaGUubGFicy5pbnRlbGxpai5uZXQvY2FjaGUv");
    stringThree = new String(decodedBytes, CharsetToolkit.UTF8_CHARSET);
  }

  @NotNull
  @Override
  public Set<String> getAllCacheKeys() {
    TemporaryCacheEntryDto[] responseDtos = doGetRequest(TemporaryCacheEntryDto[].class);
    if (responseDtos == null) return Collections.emptySet();
    return Arrays.stream(responseDtos).map(TemporaryCacheEntryDto::getName).collect(Collectors.toSet());
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

  private <T> T doGetRequest(Class<T> responseClass) {
    try {
      return HttpRequests.request(stringThree + REPOSITORY_NAME + "/caches/?json=1")
        .connect(it -> {
          URLConnection connection = it.getConnection();
          if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection)connection;
            if (httpConnection.getResponseCode() == 200) {
              InputStream inputStream = httpConnection.getInputStream();
              return OBJECT_MAPPER.readValue(inputStream, responseClass);
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

  private static class TemporaryCacheEntryDto {
    private String name;
    private String type;
    private String mtime;

    private String getName() {
      return name;
    }

    private void setName(String name) {
      this.name = name;
    }

    private String getType() {
      return type;
    }

    private void setType(String type) {
      this.type = type;
    }

    private String getMtime() {
      return mtime;
    }

    private void setMtime(String mtime) {
      this.mtime = mtime;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      TemporaryCacheEntryDto dto = (TemporaryCacheEntryDto)o;
      if (name != null ? !name.equals(dto.name) : dto.name != null) return false;
      if (type != null ? !type.equals(dto.type) : dto.type != null) return false;
      if (mtime != null ? !mtime.equals(dto.mtime) : dto.mtime != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = name != null ? name.hashCode() : 0;
      result = 31 * result + (type != null ? type.hashCode() : 0);
      result = 31 * result + (mtime != null ? mtime.hashCode() : 0);
      return result;
    }
  }
}