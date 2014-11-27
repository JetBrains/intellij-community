/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.diagnostic.IdeErrorsDialog;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.*;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.UrlConnectionUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.net.NetUtils;
import com.intellij.util.ui.UIUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;

/**
 * See XML file by {@link com.intellij.openapi.application.ex.ApplicationInfoEx#getUpdateUrls()} for reference.
 *
 * @author mike
 * @since Oct 31, 2002
 */
public final class UpdateChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdateChecker");

  private static final Map<String, PluginDownloader> ourUpdatedPlugins = new HashMap<String, PluginDownloader>();

  public enum DownloadPatchResult {
    SUCCESS, FAILED, CANCELED
  }

  private static final NotNullLazyValue<NotificationGroup> GROUP = new NotNullLazyValue<NotificationGroup>() {
    @NotNull
    @Override
    protected NotificationGroup compute() {
      return new NotificationGroup(IdeBundle.message("update.available.group"), NotificationDisplayType.STICKY_BALLOON, true);
    }
  };

  @NonNls private static final String INSTALLATION_UID = "installation.uid";
  @NonNls private static final String DISABLED_UPDATE = "disabled_update.txt";

  private static Set<String> ourDisabledToUpdatePlugins;
  private static Map<String, String> ourAdditionalRequestOptions = new HashMap<String, String>();

  private static class Holder {
    private static final String UPDATE_URL = ApplicationInfoEx.getInstanceEx().getUpdateUrls().getCheckingUrl();
    private static final String PATCHES_URL = ApplicationInfoEx.getInstanceEx().getUpdateUrls().getPatchesUrl();
  }

  private UpdateChecker() { }

  private static String getUpdateUrl() {
    String url = System.getProperty("idea.updates.url");
    return url != null ? url : Holder.UPDATE_URL;
  }

  private static String getPatchesUrl() {
    String url = System.getProperty("idea.patches.url");
    return url != null ? url : Holder.PATCHES_URL;
  }

  // for scheduled update checks
  public static ActionCallback updateAndShowResult() {
    final ActionCallback callback = new ActionCallback();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        doUpdateAndShowResult(null, true, false, UpdateSettings.getInstance(), null, callback);
      }
    });
    return callback;
  }

  // for manual update checks (Help | Check for Updates)
  public static void updateAndShowResult(final @Nullable Project project,
                                         final boolean fromSettings,
                                         final UpdateSettings settings) {
    ProgressManager.getInstance().run(new Task.Backgroundable(project, IdeBundle.message("updates.checking.progress"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        doUpdateAndShowResult(project, !fromSettings, true, settings, indicator, null);
      }

      @Override
      public boolean isConditionalModal() {
        return fromSettings;
      }

      @Override
      public boolean shouldStartInBackground() {
        return !fromSettings;
      }
    });
  }

  private static void doUpdateAndShowResult(final @Nullable Project project,
                                            final boolean enableLink,
                                            final boolean manualCheck,
                                            final UpdateSettings updateSettings,
                                            final @Nullable ProgressIndicator indicator,
                                            final @Nullable ActionCallback callback) {
    final CheckForUpdateResult result = checkForUpdates(updateSettings);

    if (manualCheck && result.getState() == UpdateStrategy.State.LOADED) {
      UpdateSettings settings = UpdateSettings.getInstance();
      settings.saveLastCheckedInfo();
      settings.setKnownChannelIds(result.getAllChannelsIds());
    }
    else if (result.getState() == UpdateStrategy.State.CONNECTION_ERROR) {
      //noinspection ThrowableResultOfMethodCallIgnored
      showErrorMessage(manualCheck,
                       result.getError() instanceof InterruptedIOException ? IdeBundle.message("updates.timeout.error") : IdeBundle.message("updates.error.connection.failed"));
      return;
    }

    final UpdateChannel updatedChannel = result.getUpdatedChannel();
    boolean platformUpdate = newChannelReady(result.getChannelToPropose());
    BuildNumber buildNumber = null;
    if (updatedChannel != null) {
      final BuildInfo latestBuild = updatedChannel.getLatestBuild();
      if (latestBuild != null) {
        buildNumber = latestBuild.getNumber();
      }
    }
    final Collection<IdeaPluginDescriptor> incompatiblePlugins = buildNumber != null ? new HashSet<IdeaPluginDescriptor>() : null;
    final Collection<PluginDownloader> updatedPlugins = platformUpdate ? null : updatePlugins(manualCheck, incompatiblePlugins, indicator, buildNumber);

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        showUpdateResult(project, result, updatedPlugins, incompatiblePlugins, enableLink, manualCheck);
        if (callback != null) {
          callback.setDone();
        }
      }
    });
  }

  public static Collection<PluginDownloader> updatePlugins(boolean manualCheck,
                                                           @Nullable Collection<IdeaPluginDescriptor> incompatiblePlugins,
                                                           @Nullable ProgressIndicator indicator,
                                                           @Nullable BuildNumber buildNumber) {
    final Map<PluginId, PluginDownloader> downloaded = new HashMap<PluginId, PluginDownloader>();
    final Set<String> failed = new HashSet<String>();
    for (String host : getPluginHosts()) {
      try {
        checkPluginsHost(host, downloaded, incompatiblePlugins, true, indicator, buildNumber);
      }
      catch (ProcessCanceledException e) {
        return null;
      }
      catch (Exception e) {
        LOG.info(e);
        failed.add(host);
      }
    }

    final Map<String, IdeaPluginDescriptor> toUpdate = new HashMap<String, IdeaPluginDescriptor>();
    final IdeaPluginDescriptor[] installedPlugins = PluginManagerCore.getPlugins();
    for (IdeaPluginDescriptor installedPlugin : installedPlugins) {
      if (!installedPlugin.isBundled()) {
        toUpdate.put(installedPlugin.getPluginId().getIdString(), installedPlugin);
      }
    }

    for (Iterator<PluginId> iterator = downloaded.keySet().iterator(); iterator.hasNext(); ) {
      if (!toUpdate.containsKey(iterator.next().getIdString())) {
        iterator.remove();
      }
    }

    final File installedTxt = new File(PathManager.getConfigPath(), PluginManager.INSTALLED_TXT);
    if (installedTxt.isFile()) {
      try {
        final String oldInstalledPlugins = FileUtil.loadFile(installedTxt);
        for (String pluginId : oldInstalledPlugins.trim().split("\n")) {
          if (!toUpdate.containsKey(pluginId)) toUpdate.put(pluginId.trim(), null);
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
      installedTxt.deleteOnExit();
    }

    final PluginManagerUISettings updateSettings = PluginManagerUISettings.getInstance();
    updateSettings.myOutdatedPlugins.clear();
    if (!toUpdate.isEmpty()) {
      try {
        final List<IdeaPluginDescriptor> process = RepositoryHelper.loadPluginsFromRepository(indicator, buildNumber);
        for (IdeaPluginDescriptor loadedPlugin : process) {
          final PluginId pluginId = loadedPlugin.getPluginId();
          final String idString = pluginId.getIdString();
          if (!toUpdate.containsKey(idString)) continue;
          if (!downloaded.containsKey(pluginId)) {
            prepareToInstall(PluginDownloader.createDownloader(loadedPlugin, buildNumber), buildNumber, downloaded, incompatiblePlugins, true, indicator);
          }
        }
      }
      catch (ProcessCanceledException ignore) {
        return null;
      }
      catch (Exception e) {
        showErrorMessage(manualCheck, e.getMessage());
      }
    }

    if (!failed.isEmpty()) {
      LOG.warn(IdeBundle.message("updates.error.plugin.description.failed", StringUtil.join(failed, ",")));
    }

    return downloaded.isEmpty() ? null : downloaded.values();
  }

  private static boolean isReadyToUpdate(String idString, String newVersion) {
    final PluginDownloader oldPlugin = ourUpdatedPlugins.get(idString);
    return oldPlugin == null || StringUtil.compareVersionNumbers(newVersion, oldPlugin.getPluginVersion()) > 0;
  }

  private static void prepareToInstall(PluginDownloader downloader, 
                                       BuildNumber buildNumber,
                                       Map<PluginId, PluginDownloader> downloaded,
                                       Collection<IdeaPluginDescriptor> incompatiblePlugins,
                                       boolean collectToUpdate, 
                                       @Nullable ProgressIndicator indicator) throws IOException {
    final String pluginId = downloader.getPluginId();
    final String pluginVersion = downloader.getPluginVersion();
    if (collectToUpdate && PluginManagerCore.getDisabledPlugins().contains(pluginId)) return;
    final IdeaPluginDescriptor installedPlugin = PluginManager.getPlugin(PluginId.getId(pluginId));
    if (installedPlugin == null || pluginVersion == null ||
        PluginDownloader.compareVersionsSkipBroken(installedPlugin, pluginVersion) > 0) {

      IdeaPluginDescriptor descriptor = null;
      if (isReadyToUpdate(pluginId, pluginVersion)) {
        descriptor = downloader.getDescriptor();
        if (descriptor == null) {
          if (downloader.prepareToInstall(indicator == null ? new EmptyProgressIndicator() : indicator, buildNumber)) {
            descriptor = downloader.getDescriptor();
          }
          ourUpdatedPlugins.put(pluginId, downloader);
        }
      } else {
        final PluginDownloader oldDownloader = ourUpdatedPlugins.get(pluginId);
        if (oldDownloader != null) {
          downloader = oldDownloader;
          descriptor = oldDownloader.getDescriptor();
        }
      }

      if (descriptor != null &&
          !PluginManagerCore.isIncompatible(descriptor, buildNumber) &&
          !InstalledPluginsTableModel.wasUpdated(descriptor.getPluginId())) {
        downloaded.put(PluginId.getId(pluginId), downloader);
      }
    }

    //collect plugins which were not updated and would be incompatible with new version
    if (incompatiblePlugins != null &&
        installedPlugin != null && installedPlugin.isEnabled() && !downloaded.containsKey(installedPlugin.getPluginId()) &&
        PluginManagerCore.isIncompatible(installedPlugin, buildNumber)) {
      incompatiblePlugins.add(installedPlugin);
    }
  }

  private static void showErrorMessage(boolean showDialog, final String message) {
    if (showDialog) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(message, IdeBundle.message("updates.error.connection.title"));
        }
      });
    }
    else {
      LOG.warn(message);
    }
  }

  private static List<String> getPluginHosts() {
    final List<String> hosts = UpdateSettings.getInstance().getPluginHosts();
    ContainerUtil.addIfNotNull(ApplicationInfoEx.getInstanceEx().getBuiltinPluginsUrl(), hosts);
    return hosts;
  }

  public static boolean checkPluginsHost(final String host, final Map<PluginId, PluginDownloader> downloaded, @NotNull ProgressIndicator progressIndicator) throws Exception {
    try {
      return checkPluginsHost(host, downloaded, null, true, progressIndicator, null);
    }
    catch (ProcessCanceledException e) {
      return false;
    }
  }

  public static boolean checkPluginsHost(String host,
                                         Map<PluginId, PluginDownloader> downloaded,
                                         boolean collectToUpdate,
                                         @Nullable ProgressIndicator indicator) throws Exception {
    return checkPluginsHost(host, downloaded, null, collectToUpdate, indicator, null);
  }

  private static boolean checkPluginsHost(@NotNull String host,
                                          final Map<PluginId, PluginDownloader> downloaded,
                                          final @Nullable Collection<IdeaPluginDescriptor> incompatiblePlugins,
                                          boolean collectToUpdate,
                                          @Nullable final ProgressIndicator indicator,
                                          final BuildNumber buildNumber) throws Exception {
    String url;
    if (StandardFileSystems.FILE_PROTOCOL.equals(new URL(host).getProtocol())) {
      url = host;
    }
    else {
      url = host + (host.contains("?") ? '&' : '?') + "build=" + ApplicationInfo.getInstance().getBuild().asString();
    }

    BufferExposingByteArrayOutputStream bytes = HttpRequests.request(url).connect(
      new HttpRequests.RequestProcessor<BufferExposingByteArrayOutputStream>() {
        @Override
        public BufferExposingByteArrayOutputStream process(@NotNull HttpRequests.Request request) throws IOException {
          BufferExposingByteArrayOutputStream output = new BufferExposingByteArrayOutputStream();
          try {
            NetUtils.copyStreamContent(indicator, request.getInputStream(), output, request.getConnection().getContentLength());
          }
          finally {
            output.close();
          }
          return output;
        }
      });

    ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes.getInternalBuffer(), 0, bytes.size());
    final Document document;
    try {
      document = JDOMUtil.loadDocument(inputStream);
    }
    catch (JDOMException e) {
      return false;
    }
    finally {
      inputStream.reset();
    }

    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    RepositoryContentHandler handler = new RepositoryContentHandler();
    parser.parse(inputStream, handler);

    for (IdeaPluginDescriptor descriptor : handler.getPluginsList()) {
      ((PluginNode)descriptor).setRepositoryName(host);
      prepareToInstall(PluginDownloader.createDownloader(descriptor, buildNumber), buildNumber, downloaded, incompatiblePlugins, collectToUpdate,
                       indicator);
    }

    boolean success = true;
    for (Element pluginElement : document.getRootElement().getChildren("plugin")) {
      final String pluginId = pluginElement.getAttributeValue("id");
      String pluginUrl = pluginElement.getAttributeValue("url");
      final String pluginVersion = pluginElement.getAttributeValue("version");
      Element descriptionElement = pluginElement.getChild("description");
      String description = descriptionElement != null ? descriptionElement.getText() : null;

      List<PluginId> dependsPlugins = new SmartList<PluginId>();
      for (Element depend : pluginElement.getChildren("depends")) {
        dependsPlugins.add(PluginId.getId(depend.getText()));
      }

      if (pluginId == null) {
        LOG.info("plugin id should not be null");
        success = false;
        continue;
      }

      if (pluginUrl == null) {
        LOG.info("plugin url should not be null");
        success = false;
        continue;
      }

      VirtualFile pluginFile = PluginDownloader.findPluginFile(pluginUrl, host);
      if (pluginFile == null) {
        continue;
      }

      if (collectToUpdate) {
        final String finalPluginUrl = getPluginUrl(pluginFile);
        if (ApplicationManager.getApplication().isDispatchThread()) {
          ProgressManager.getInstance().run(new Task.Modal(null, IdeBundle.message("update.uploading.plugin.progress.title"), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              updatePlugins(finalPluginUrl, pluginId, pluginVersion, buildNumber, downloaded, incompatiblePlugins, true, indicator);
            }
          });
        }
        else {
          updatePlugins(finalPluginUrl, pluginId, pluginVersion, buildNumber, downloaded, incompatiblePlugins, true, indicator);
        }
      }
      else {
        final PluginDownloader downloader = new PluginDownloader(pluginId, pluginUrl, pluginVersion);
        downloader.setDescription(description);
        downloader.setDepends(dependsPlugins);
        downloaded.put(PluginId.getId(pluginId), downloader);
      }
    }
    return success;
  }

  private static void updatePlugins(String finalPluginUrl,
                                    String pluginId,
                                    String pluginVersion,
                                    BuildNumber buildNumber,
                                    Map<PluginId, PluginDownloader> downloaded,
                                    Collection<IdeaPluginDescriptor> incompatiblePlugins, boolean collectToUpdate,
                                    ProgressIndicator indicator) {
    try {
      if (indicator != null) {
        indicator.setText2(finalPluginUrl);
      }
      PluginDownloader downloader = new PluginDownloader(pluginId, finalPluginUrl, pluginVersion, null, null, buildNumber);
      prepareToInstall(downloader, buildNumber, downloaded, incompatiblePlugins, collectToUpdate, indicator);
    }
    catch (IOException e) {
      LOG.info(e);
    }
  }

  @NotNull
  private static String getPluginUrl(@NotNull VirtualFile pluginFile) {
    String protocol = pluginFile.getFileSystem().getProtocol();
    if (StandardFileSystems.FILE_PROTOCOL.equals(protocol) && SystemInfo.isWindows) {
      String path = pluginFile.getPath();
      if (path.length() != 0 && path.charAt(0) != '/') {
        return protocol + ":///" + path;  // fix file URI on Windows
      }
    }

    return pluginFile.getUrl();
  }

  @Nullable
  private static UpdatesInfo loadUpdatesInfo(@Nullable String updateUrl) throws Exception {
    if (LOG.isDebugEnabled()) {
      LOG.debug("load update xml (UPDATE_URL='" + updateUrl + "' )");
    }

    if (StringUtil.isEmpty(updateUrl)) {
      LOG.debug("update url is empty: updates will not be checked");
      return null;
    }

    if (!updateUrl.startsWith("file:")) {
      updateUrl = updateUrl + '?' + prepareUpdateCheckArgs();
    }
    return HttpRequests.request(updateUrl).connect(new HttpRequests.RequestProcessor<UpdatesInfo>() {
      @Override
      public UpdatesInfo process(@NotNull HttpRequests.Request request) throws IOException {
        try {
          Document document = JDOMUtil.loadDocument(request.getInputStream());
          return new UpdatesInfo(document.detachRootElement());
        }
        catch (JDOMException e) {
          // corrupted content, don't bother telling user
          LOG.info(e);
          return null;
        }
      }
    });
  }

  @NotNull
  private static CheckForUpdateResult checkForUpdates(final UpdateSettings settings) {
    UpdatesInfo info;
    try {
      info = loadUpdatesInfo(getUpdateUrl());
      if (info == null) {
        return new CheckForUpdateResult(UpdateStrategy.State.NOTHING_LOADED);
      }
    }
    catch (Exception e) {
      return new CheckForUpdateResult(UpdateStrategy.State.CONNECTION_ERROR, e);
    }

    ApplicationInfo appInfo = ApplicationInfo.getInstance();
    int majorVersion = Integer.parseInt(appInfo.getMajorVersion());
    UpdateStrategy strategy = new UpdateStrategy(majorVersion, appInfo.getBuild(), info, settings);
    return strategy.checkForUpdates();
  }

  public static void addUpdateRequestParameter(String name) {
    addUpdateRequestParameter(name, "");
  }

  public static void addUpdateRequestParameter(@NotNull String name, @NotNull String value) {
    ourAdditionalRequestOptions.put(name, value);
  }

  @Contract("null -> false")
  private static boolean newChannelReady(@Nullable UpdateChannel channelToPropose) {
    return channelToPropose != null && channelToPropose.getLatestBuild() != null;
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  private static void showUpdateResult(@Nullable final Project project,
                                       final CheckForUpdateResult checkForUpdateResult,
                                       final Collection<PluginDownloader> updatedPlugins,
                                       final Collection<IdeaPluginDescriptor> incompatiblePlugins, 
                                       final boolean enableLink,
                                       final boolean alwaysShowResults) {
    final UpdateChannel channelToPropose = checkForUpdateResult.getChannelToPropose();
    final UpdateChannel updatedChannel = checkForUpdateResult.getUpdatedChannel();

    if (newChannelReady(channelToPropose)) {
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          new NewChannelDialog(channelToPropose).show();
        }
      };

      if (alwaysShowResults) {
        runnable.run();
      }
      else {
        String message = IdeBundle.message("updates.new.version.available", ApplicationNamesInfo.getInstance().getFullProductName());
        showNotification(project, message, false, runnable);
      }
    }
    else if (updatedChannel != null) {
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          new UpdateInfoDialog(updatedChannel, enableLink, updatedPlugins, incompatiblePlugins).show();
        }
      };

      if (alwaysShowResults) {
        runnable.run();
      }
      else {
        String message = IdeBundle.message("updates.ready.message", ApplicationNamesInfo.getInstance().getFullProductName());
        showNotification(project, message, false, runnable);
      }
    }
    else if (updatedPlugins != null && !updatedPlugins.isEmpty()) {
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          new PluginUpdateInfoDialog(updatedPlugins, enableLink).show();
        }
      };

      if (alwaysShowResults) {
        runnable.run();
      }
      else {
        String plugins = StringUtil.join(updatedPlugins, new Function<PluginDownloader, String>() {
          @Override
          public String fun(PluginDownloader downloader) {
            return downloader.getPluginName();
          }
        }, ", ");
        String message = IdeBundle.message("updates.plugins.ready.message", updatedPlugins.size(), plugins);
        showNotification(project, message, false, runnable);
      }
    }
    else if (alwaysShowResults) {
      new NoUpdatesDialog(enableLink).show();
    }
  }

  private static void showNotification(@Nullable Project project, String message, boolean error, final @Nullable Runnable runnable) {
    NotificationListener listener = null;
    if (runnable != null) {
      listener = new NotificationListener() {
        @Override
        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          notification.expire();
          runnable.run();
        }
      };
    }

    String title = IdeBundle.message("updates.info.dialog.title");
    NotificationType type = error ? NotificationType.ERROR : NotificationType.INFORMATION;
    Notifications.Bus.notify(GROUP.getValue().createNotification(title, message, type, listener), project);
  }

  public static String prepareUpdateCheckArgs() {
    addUpdateRequestParameter("build", ApplicationInfo.getInstance().getBuild().asString());
    addUpdateRequestParameter("uid", getInstallationUID(PropertiesComponent.getInstance()));
    addUpdateRequestParameter("os", SystemInfo.OS_NAME + ' ' + SystemInfo.OS_VERSION);
    if (ApplicationInfoEx.getInstanceEx().isEAP()) {
      addUpdateRequestParameter("eap");
    }

    StringBuilder args = new StringBuilder();

    try {
      for (String name : ourAdditionalRequestOptions.keySet()) {
        if (args.length() > 0) {
          args.append('&');
        }

        args.append(URLEncoder.encode(name, CharsetToolkit.UTF8));

        String value = ourAdditionalRequestOptions.get(name);
        if (!StringUtil.isEmpty(value)) {
          args.append('=').append(URLEncoder.encode(value, CharsetToolkit.UTF8));
        }
      }

      return args.toString();
    }
    catch (UnsupportedEncodingException e) {
      return ""; // Can't be anyway
    }
  }

  public static String getInstallationUID(final PropertiesComponent propertiesComponent) {
    if (SystemInfo.isWindows) {
      String uid = getInstallationUIDOnWindows(propertiesComponent);
      if (uid != null) {
        return uid;
      }
    }
    String uid;
    if (!propertiesComponent.isValueSet(INSTALLATION_UID)) {
      uid = generateUUID();
      propertiesComponent.setValue(INSTALLATION_UID, uid);
    }
    else {
      uid = propertiesComponent.getValue(INSTALLATION_UID);
    }
    return uid;
  }

  @Nullable
  private static String getInstallationUIDOnWindows(PropertiesComponent propertiesComponent) {
    String appdata = System.getenv("APPDATA");
    if (appdata != null) {
      File jetBrainsDir = new File(appdata, "JetBrains");
      if (jetBrainsDir.exists() || jetBrainsDir.mkdirs()) {
        File permanentIdFile = new File(jetBrainsDir, "PermanentUserId");
        try {
          if (permanentIdFile.exists()) {
            return FileUtil.loadFile(permanentIdFile).trim();
          }

          String uuid;
          if (propertiesComponent.isValueSet(INSTALLATION_UID)) {
            uuid = propertiesComponent.getValue(INSTALLATION_UID);
          }
          else {
            uuid = generateUUID();
          }
          FileUtil.writeToFile(permanentIdFile, uuid);
          return uuid;
        }
        catch (IOException ignored) { }
      }
    }

    return null;
  }

  private static String generateUUID() {
    try {
      return UUID.randomUUID().toString();
    }
    catch (Exception ignored) { }
    catch (InternalError ignored) { }

    return "";
  }

  public static boolean install(@NotNull Collection<PluginDownloader> downloaders, @NotNull ProgressIndicator progressIndicator) {
    boolean installed = false;
    for (PluginDownloader downloader : downloaders) {
      if (getDisabledToUpdatePlugins().contains(downloader.getPluginId())) continue;
      try {
        if (downloader.prepareToInstall(progressIndicator)) {
          final IdeaPluginDescriptor descriptor = downloader.getDescriptor();
          if (descriptor != null) {
            InstalledPluginsTableModel.updateExistingPlugin(descriptor, PluginManager.getPlugin(descriptor.getPluginId()));
            downloader.install();
            installed = true;
          }
        }
      }
      catch (IOException e) {
        LOG.info(e);
      }
    }
    return installed;
  }

  public static DownloadPatchResult downloadAndInstallPatch(final BuildInfo newVersion) {
    final DownloadPatchResult[] result = new DownloadPatchResult[]{DownloadPatchResult.CANCELED};

    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        try {
          doDownloadAndInstallPatch(newVersion, ProgressManager.getInstance().getProgressIndicator());
          result[0] = DownloadPatchResult.SUCCESS;
        }
        catch (final IOException e) {
          LOG.info(e);
          result[0] = DownloadPatchResult.FAILED;
          Notifications.Bus.notify(new Notification("Updater", "Failed to download patch file", e.getMessage(), NotificationType.ERROR));
        }
      }
    }, IdeBundle.message("update.downloading.patch.progress.title"), true, null)) {
      return DownloadPatchResult.CANCELED;
    }

    return result[0];
  }

  private static void doDownloadAndInstallPatch(BuildInfo newVersion, ProgressIndicator i) throws IOException {
    PatchInfo patch = newVersion.findPatchForCurrentBuild();
    if (patch == null) throw new IOException("No patch is available for current version");

    String productCode = ApplicationInfo.getInstance().getBuild().getProductCode();

    String osSuffix = "-" + patch.getOSSuffix();

    String fromBuildNumber = patch.getFromBuild().asStringWithoutProductCode();
    String toBuildNumber = newVersion.getNumber().asStringWithoutProductCode();
    String jdkMacRedist = System.getProperty("idea.java.redist");
    String bundledJdk = "";
    if (jdkMacRedist != null && jdkMacRedist.lastIndexOf("jdk-bundled") >= 0 ){
      bundledJdk = "jdk-bundled".equals(jdkMacRedist) ? "-jdk-bundled" : "-custom-jdk-bundled";
    }

    String fileName = productCode + "-" + fromBuildNumber + "-" + toBuildNumber + "-patch" + bundledJdk + osSuffix + ".jar";

    String platform = PlatformUtils.getPlatformPrefix();

    File tempFile = FileUtil.createTempFile(platform, "patch", true);

    OutputStream out = new BufferedOutputStream(new FileOutputStream(tempFile));
    try {
      URLConnection connection = HttpConfigurable.getInstance().openConnection(new URL(new URL(getPatchesUrl()), fileName).toString());
      try {
        InputStream in = UrlConnectionUtil.getConnectionInputStreamWithException(connection, i);
        try {
          int total = connection.getContentLength();
          i.setIndeterminate(total <= 0);

          byte[] buffer = new byte[10 * 1024];
          int count;
          int read = 0;

          while ((count = in.read(buffer)) > 0) {
            i.checkCanceled();
            out.write(buffer, 0, count);
            read += count;
            if (total > 0) {
              i.setFraction(((double)read) / total);
              i.setText2((read / 1024) + "/" + (total / 1024) + " KB");
            }
            else {
              i.setText2((read / 1024) + " KB");
            }
          }
        }
        finally {
          in.close();
        }
      }
      finally {
        if (connection instanceof HttpURLConnection) {
          ((HttpURLConnection)connection).disconnect();
        }
      }
    }
    finally {
      out.close();
    }

    String patchFileName = ("jetbrains.patch.jar." + platform).toLowerCase();
    File patchFile = new File(FileUtil.getTempDirectory(), patchFileName);
    FileUtil.copy(tempFile, patchFile);
    FileUtil.delete(tempFile);
  }

  public static Set<String> getDisabledToUpdatePlugins() {
    if (ourDisabledToUpdatePlugins == null) {
      ourDisabledToUpdatePlugins = new TreeSet<String>();
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        try {
          final File file = new File(PathManager.getConfigPath(), DISABLED_UPDATE);
          if (file.isFile()) {
            final String[] ids = FileUtil.loadFile(file).split("[\\s]");
            for (String id : ids) {
              if (id != null && id.trim().length() > 0) {
                ourDisabledToUpdatePlugins.add(id.trim());
              }
            }
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    return ourDisabledToUpdatePlugins;
  }

  public static void saveDisabledToUpdatePlugins() {
    final File plugins = new File(PathManager.getConfigPath(), DISABLED_UPDATE);
    try {
      PluginManagerCore.savePluginsList(getDisabledToUpdatePlugins(), false, plugins);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private static boolean ourHasFailedPlugins = false;
  public static void checkForUpdate(IdeaLoggingEvent event) {
    if (!ourHasFailedPlugins && UpdateSettings.getInstance().CHECK_NEEDED) {
      final Throwable throwable = event.getThrowable();
      final IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(IdeErrorsDialog.findPluginId(throwable));
      if (pluginDescriptor != null && !pluginDescriptor.isBundled()) {
        ourHasFailedPlugins = true;
        updateAndShowResult();
      }
    }
  }

  static String getDownloadUrl(IdeaPluginDescriptor descriptor, @Nullable BuildNumber buildNumber) throws UnsupportedEncodingException {
    String url = null;
    if (descriptor instanceof PluginNode) {
      url = ((PluginNode)descriptor).getDownloadUrl();
      if (url != null) {
        final String repositoryName = ((PluginNode)descriptor).getRepositoryName();
        if (repositoryName != null) {
          final VirtualFile pluginFile = PluginDownloader.findPluginFile(url, repositoryName);
          if (pluginFile != null) {
            url = getPluginUrl(pluginFile);
          }
        }
      }
    }

    if (url == null) {
      String uuid = ApplicationManager.getApplication() == null ?
                    UUID.randomUUID().toString() :
                    getInstallationUID(PropertiesComponent.getInstance());
      String buildNumberAsString = buildNumber != null
                                   ? buildNumber.asString()
                                   : ApplicationManager.getApplication() != null
                                     ? ApplicationInfo.getInstance().getApiVersion()
                                     : ApplicationInfoImpl.getShadowInstance().getBuild().asString();
      url = RepositoryHelper.getDownloadUrl() + URLEncoder.encode(descriptor.getPluginId().getIdString(), "UTF8") +
            "&build=" + buildNumberAsString + "&uuid=" + URLEncoder.encode(uuid, "UTF8");
    }
    return url;
  }

}
