package com.intellij.jps.cache.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.jps.cache.JpsCachesPluginUtil;
import com.intellij.jps.cache.model.AffectedModule;
import com.intellij.jps.cache.model.OutputLoadResult;
import com.intellij.jps.cache.ui.JpsLoaderNotifications;
import com.intellij.jps.cache.ui.SegmentedProgressIndicatorManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
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
  private static final String REPOSITORY_NAME = "";
  private final String stringThree;

  private TemporaryCacheServerClient() {
    byte[] decodedBytes = Base64.getDecoder().decode("aHR0cHM6Ly90ZW1wb3JhcnktZmlsZXMtY2FjaGUubGFicy5qYi5nZy9jYWNoZS8=");
    stringThree = "https://d1lc5k9lerg6km.cloudfront.net";
  }

  @NotNull
  @Override
  public Set<String> getAllCacheKeys(@NotNull Project project) {
    Map<String, List<String>> response = doGetRequest(project, getRequestHeaders());
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
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    JpsCachesDownloader downloader = new JpsCachesDownloader(Collections.singletonList(description),
                                                             new SegmentedProgressIndicatorManager(progressIndicator));

    LOG.debug("Downloading JPS metadata from: " + downloadUrl);
    File metadataFile;
    try {
      List<Pair<File, DownloadableFileDescription>> pairs = downloader.download(targetDir, getRequestHeaders());
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
    long start = System.currentTimeMillis();
    String downloadUrl = stringThree + REPOSITORY_NAME + "/caches/" + cacheId;
    String fileName = "portable-build-cache.zip";
    DownloadableFileService service = DownloadableFileService.getInstance();
    DownloadableFileDescription description = service.createFileDescription(downloadUrl, fileName);
    JpsCachesDownloader downloader = new JpsCachesDownloader(Collections.singletonList(description), downloadIndicatorManager);

    LOG.debug("Downloading JPS caches from: " + downloadUrl);
    File zipFile;
    try {
      List<Pair<File, DownloadableFileDescription>> pairs = downloader.download(targetDir, getRequestHeaders());
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
    JpsCachesDownloader downloader = new JpsCachesDownloader(descriptions, downloadIndicatorManager);

    List<File> downloadedFiles = new ArrayList<>();
    try {
      // Downloading process
      List<Pair<File, DownloadableFileDescription>> download = downloader.download(targetDir, getRequestHeaders());
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

  private Map<String, List<String>> doGetRequest(@NotNull Project project, @NotNull Map<String, String> headers) {
    try {
      return HttpRequests.request(stringThree + REPOSITORY_NAME + "/commit_history.json")
        .tuner(tuner -> headers.forEach((k, v) -> tuner.addRequestProperty(k, v)))
        .connect(it -> {
          URLConnection connection = it.getConnection();
          if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection)connection;
            if (httpConnection.getResponseCode() == 200) {
              return OBJECT_MAPPER.readValue(getInputStream(httpConnection), new TypeReference<Map<String, List<String>>>() {});
            }
            else {
              String statusLine = httpConnection.getResponseCode() + " " + httpConnection.getRequestMethod();
              InputStream errorStream = httpConnection.getErrorStream();
              String errorText = StreamUtil.readText(errorStream, StandardCharsets.UTF_8);
              LOG.info("Request: " + httpConnection.getRequestMethod() + httpConnection.getURL() + " : Error " + statusLine + " body: " +
                        errorText);
            }
          }
          return null;
        });
    }
    catch (IOException e) {
      LOG.warn("Failed request to cache server", e);
      Notification notification = JpsLoaderNotifications.NONE_NOTIFICATION_GROUP
        .createNotification("Compiler Caches Loader", "Failed request to cache server: " + e.getMessage(), NotificationType.ERROR, null);
      Notifications.Bus.notify(notification, project);
    }
    return null;
  }

  private static @NotNull Map<String, String> getRequestHeaders() {
    Optional<JpsServerAuthExtension> optional = JpsServerAuthExtension.EP_NAME.extensions().findFirst();
    if (!optional.isPresent()) return Collections.emptyMap();
    Map<String, String> authHeader = optional.get().getAuthHeader();
    if (authHeader == null) return Collections.emptyMap();
    return authHeader;
  }

  private static InputStream getInputStream(HttpURLConnection httpConnection) throws IOException {
    String contentEncoding = httpConnection.getContentEncoding();
    InputStream inputStream = httpConnection.getInputStream();
    if (contentEncoding != null && StringUtil.toLowerCase(contentEncoding).contains("gzip")) {
      return new GZIPInputStream(inputStream);
    }
    return inputStream;
  }
}