package com.intellij.jps.cache.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  private <T> T doGetRequest(Class<T> responseClass) {
    try {
      return HttpRequests.request(stringThree + REPOSITORY_NAME + "/caches?json=1")
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
      LOG.warn("Failed request to cache artifactory", e);
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