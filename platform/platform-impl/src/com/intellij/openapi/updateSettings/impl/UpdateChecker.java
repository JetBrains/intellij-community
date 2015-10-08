/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.apache.http.client.utils.URIBuilder;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

/**
 * See XML file by {@link ApplicationInfoEx#getUpdateUrls()} for reference.
 *
 * @author mike
 * @since Oct 31, 2002
 */
public final class UpdateChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.updateSettings.impl.UpdateChecker");

  public static final NotificationGroup NOTIFICATIONS =
    new NotificationGroup(IdeBundle.message("update.notifications.group"), NotificationDisplayType.STICKY_BALLOON, true);

  @NonNls private static final String INSTALLATION_UID = "installation.uid";
  @NonNls private static final String DISABLED_UPDATE = "disabled_update.txt";

  private static Set<String> ourDisabledToUpdatePlugins;
  private static final Map<String, String> ourAdditionalRequestOptions = ContainerUtil.newHashMap();
  private static final Map<String, PluginDownloader> ourUpdatedPlugins = ContainerUtil.newHashMap();

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

  /**
   * For scheduled update checks.
   */
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

  /**
   * For manual update checks (Help | Check for Updates, Settings | Updates | Check Now)
   * (the latter action may pass customised update settings).
   */
  public static void updateAndShowResult(@Nullable Project project, @Nullable UpdateSettings customSettings) {
    final UpdateSettings settings = customSettings != null ? customSettings : UpdateSettings.getInstance();
    final boolean fromSettings = customSettings != null;

    ProgressManager.getInstance().run(new Task.Backgroundable(project, IdeBundle.message("updates.checking.progress"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        doUpdateAndShowResult(getProject(), !fromSettings, true, settings, indicator, null);
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

  private static void doUpdateAndShowResult(@Nullable final Project project,
                                            final boolean enableLink,
                                            final boolean manualCheck,
                                            @NotNull final UpdateSettings updateSettings,
                                            @Nullable ProgressIndicator indicator,
                                            @Nullable final ActionCallback callback) {
    // check platform update

    if (indicator != null) {
      indicator.setText(IdeBundle.message("updates.checking.platform"));
    }

    final CheckForUpdateResult result = checkPlatformUpdate(updateSettings);

    if (manualCheck && result.getState() == UpdateStrategy.State.LOADED) {
      UpdateSettings settings = UpdateSettings.getInstance();
      settings.saveLastCheckedInfo();
      settings.setKnownChannelIds(result.getAllChannelsIds());
    }
    else if (result.getState() == UpdateStrategy.State.CONNECTION_ERROR) {
      Exception e = result.getError();
      if (e != null) LOG.debug(e);
      String cause = e != null ? e.getMessage() : "internal error";
      showErrorMessage(manualCheck, IdeBundle.message("updates.error.connection.failed", cause));
      return;
    }

    // check plugins update (with regard to potential platform update)

    if (indicator != null) {
      indicator.setText(IdeBundle.message("updates.checking.plugins"));
    }

    final Collection<PluginDownloader> updatedPlugins;
    final Collection<IdeaPluginDescriptor> incompatiblePlugins;

    if (newChannelReady(result.getChannelToPropose())) {
      updatedPlugins = null;
      incompatiblePlugins = null;
    }
    else {
      BuildNumber buildNumber = null;

      UpdateChannel updatedChannel = result.getUpdatedChannel();
      if (updatedChannel != null) {
        BuildInfo latestBuild = updatedChannel.getLatestBuild();
        if (latestBuild != null) {
          buildNumber = latestBuild.getNumber();
        }
      }

      incompatiblePlugins = buildNumber != null ? new HashSet<IdeaPluginDescriptor>() : null;
      updatedPlugins = checkPluginsUpdate(manualCheck, updateSettings, indicator, incompatiblePlugins, buildNumber);
    }

    // show result

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        showUpdateResult(project, result, updateSettings, updatedPlugins, incompatiblePlugins, enableLink, manualCheck);
        if (callback != null) {
          callback.setDone();
        }
      }
    }, ModalityState.NON_MODAL);
  }

  @NotNull
  private static CheckForUpdateResult checkPlatformUpdate(@NotNull UpdateSettings settings) {
    UpdatesInfo info;
    try {
      URIBuilder uriBuilder = new URIBuilder(getUpdateUrl());
      if (!URLUtil.FILE_PROTOCOL.equals(uriBuilder.getScheme())) {
        prepareUpdateCheckArgs(uriBuilder);
      }
      String updateUrl = uriBuilder.build().toString();
      LogUtil.debug(LOG, "load update xml (UPDATE_URL='%s')", updateUrl);

      info = HttpRequests.request(updateUrl).forceHttps(settings.canUseSecureConnection()).connect(new HttpRequests.RequestProcessor<UpdatesInfo>() {
        @Override
        public UpdatesInfo process(@NotNull HttpRequests.Request request) throws IOException {
          try {
            return new UpdatesInfo(JDOMUtil.load(request.getReader()));
          }
          catch (JDOMException e) {
            // corrupted content, don't bother telling user
            LOG.info(e);
            return null;
          }
        }
      });
    }
    catch (URISyntaxException e) {
      return new CheckForUpdateResult(UpdateStrategy.State.CONNECTION_ERROR, e);
    }
    catch (IOException e) {
      return new CheckForUpdateResult(UpdateStrategy.State.CONNECTION_ERROR, e);
    }
    if (info == null) {
      return new CheckForUpdateResult(UpdateStrategy.State.NOTHING_LOADED);
    }

    ApplicationInfo appInfo = ApplicationInfo.getInstance();
    int majorVersion = Integer.parseInt(appInfo.getMajorVersion());
    UpdateStrategy strategy = new UpdateStrategy(majorVersion, appInfo.getBuild(), info, settings);
    return strategy.checkForUpdates();
  }

  private static Collection<PluginDownloader> checkPluginsUpdate(boolean manualCheck,
                                                                 @NotNull UpdateSettings updateSettings,
                                                                 @Nullable ProgressIndicator indicator,
                                                                 @Nullable Collection<IdeaPluginDescriptor> incompatiblePlugins,
                                                                 @Nullable BuildNumber buildNumber) {
    // collect installed plugins and plugins imported from a previous installation
    Map<PluginId, IdeaPluginDescriptor> updateable = ContainerUtil.newTroveMap();

    for (IdeaPluginDescriptor descriptor : PluginManagerCore.getPlugins()) {
      if (!descriptor.isBundled()) {
        updateable.put(descriptor.getPluginId(), descriptor);
      }
    }

    File onceInstalled = new File(PathManager.getConfigPath(), PluginManager.INSTALLED_TXT);
    if (onceInstalled.isFile()) {
      try {
        for (String line : FileUtil.loadLines(onceInstalled)) {
          PluginId id = PluginId.getId(line.trim());
          if (!updateable.containsKey(id)) {
            updateable.put(id, null);
          }
        }
      }
      catch (IOException e) {
        LOG.error(onceInstalled.getPath(), e);
      }
      onceInstalled.deleteOnExit();
    }

    if (updateable.isEmpty()) return null;

    // check custom repositories and the main one for updates
    Map<PluginId, PluginDownloader> toUpdate = ContainerUtil.newTroveMap();

    List<String> hosts = RepositoryHelper.getPluginHosts();
    InstalledPluginsState state = InstalledPluginsState.getInstance();

    outer:
    for (String host : hosts) {
      try {
        boolean forceHttps = host == null && updateSettings.canUseSecureConnection();
        List<IdeaPluginDescriptor> list = RepositoryHelper.loadPlugins(host, buildNumber, forceHttps, indicator);
        for (IdeaPluginDescriptor descriptor : list) {
          PluginId id = descriptor.getPluginId();
          if (updateable.containsKey(id)) {
            updateable.remove(id);
            state.onDescriptorDownload(descriptor);
            PluginDownloader downloader = PluginDownloader.createDownloader(descriptor, host, buildNumber);
            downloader.setForceHttps(forceHttps);
            checkAndPrepareToInstall(downloader, state, toUpdate, incompatiblePlugins, indicator);
            if (updateable.isEmpty()) {
              break outer;
            }
          }
        }
      }
      catch (IOException e) {
        LOG.debug(e);
        if (host != null) {
          LOG.info("failed to load plugin descriptions from " + host + ": " + e.getMessage());
        }
        else {
          showErrorMessage(manualCheck, IdeBundle.message("updates.error.connection.failed", e.getMessage()));
        }
      }
    }

    return toUpdate.isEmpty() ? null : toUpdate.values();
  }

  private static void checkAndPrepareToInstall(PluginDownloader downloader,
                                               InstalledPluginsState state,
                                               Map<PluginId, PluginDownloader> toUpdate,
                                               Collection<IdeaPluginDescriptor> incompatiblePlugins,
                                               @Nullable ProgressIndicator indicator) throws IOException {
    String pluginId = downloader.getPluginId();
    if (PluginManagerCore.getDisabledPlugins().contains(pluginId)) return;

    String pluginVersion = downloader.getPluginVersion();
    IdeaPluginDescriptor installedPlugin = PluginManager.getPlugin(PluginId.getId(pluginId));
    if (installedPlugin == null || pluginVersion == null || PluginDownloader.compareVersionsSkipBroken(installedPlugin, pluginVersion) > 0) {
      IdeaPluginDescriptor descriptor;

      PluginDownloader oldDownloader = ourUpdatedPlugins.get(pluginId);
      if (oldDownloader == null || StringUtil.compareVersionNumbers(pluginVersion, oldDownloader.getPluginVersion()) > 0) {
        descriptor = downloader.getDescriptor();
        if (descriptor instanceof PluginNode && ((PluginNode)descriptor).isIncomplete()) {
          if (downloader.prepareToInstall(indicator == null ? new EmptyProgressIndicator() : indicator)) {
            descriptor = downloader.getDescriptor();
          }
          ourUpdatedPlugins.put(pluginId, downloader);
        }
      }
      else {
        downloader = oldDownloader;
        descriptor = oldDownloader.getDescriptor();
      }

      if (descriptor != null &&
          !PluginManagerCore.isIncompatible(descriptor, downloader.getBuildNumber()) &&
          !state.wasUpdated(descriptor.getPluginId())) {
        toUpdate.put(PluginId.getId(pluginId), downloader);
      }
    }

    //collect plugins which were not updated and would be incompatible with new version
    if (incompatiblePlugins != null &&
        installedPlugin != null && installedPlugin.isEnabled() && !toUpdate.containsKey(installedPlugin.getPluginId()) &&
        PluginManagerCore.isIncompatible(installedPlugin, downloader.getBuildNumber())) {
      incompatiblePlugins.add(installedPlugin);
    }
  }

  private static void showErrorMessage(boolean showDialog, final String message) {
    LOG.info(message);
    if (showDialog) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(message, IdeBundle.message("updates.error.connection.title"));
        }
      });
    }
  }

  @Contract("null -> false")
  private static boolean newChannelReady(@Nullable UpdateChannel channelToPropose) {
    return channelToPropose != null && channelToPropose.getLatestBuild() != null;
  }

  private static void showUpdateResult(@Nullable final Project project,
                                       final CheckForUpdateResult checkForUpdateResult,
                                       final UpdateSettings updateSettings,
                                       final Collection<PluginDownloader> updatedPlugins,
                                       final Collection<IdeaPluginDescriptor> incompatiblePlugins,
                                       final boolean enableLink,
                                       final boolean alwaysShowResults) {
    final UpdateChannel channelToPropose = checkForUpdateResult.getChannelToPropose();
    final UpdateChannel updatedChannel = checkForUpdateResult.getUpdatedChannel();

    if (updatedChannel != null) {
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          new UpdateInfoDialog(updatedChannel, enableLink, updateSettings.canUseSecureConnection(), updatedPlugins, incompatiblePlugins).show();
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
    else if (newChannelReady(channelToPropose)) {
      Runnable runnable = new Runnable() {
        @Override
        public void run() {
          NewChannelDialog dialog = new NewChannelDialog(channelToPropose);
          dialog.show();
          // once we informed that new product is available (when new channel was detected), remember the fact
          if(dialog.getExitCode() == DialogWrapper.CANCEL_EXIT_CODE &&
             checkForUpdateResult.getState() == UpdateStrategy.State.LOADED &&
             !updateSettings.getKnownChannelsIds().contains(channelToPropose.getId())) {
            List<String> newIds =  new ArrayList<String>(updateSettings.getKnownChannelsIds());
            newIds.add(channelToPropose.getId());
            updateSettings.setKnownChannelIds(newIds);
          }
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

  private static void showNotification(@Nullable Project project, String message, boolean error, @Nullable final Runnable runnable) {
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

    String title = IdeBundle.message("update.notifications.title");
    NotificationType type = error ? NotificationType.ERROR : NotificationType.INFORMATION;
    NOTIFICATIONS.createNotification(title, XmlStringUtil.wrapInHtml(message), type, listener).notify(project);
  }

  public static void addUpdateRequestParameter(@NotNull String name, @NotNull String value) {
    ourAdditionalRequestOptions.put(name, value);
  }

  private static void prepareUpdateCheckArgs(URIBuilder uriBuilder) {
    addUpdateRequestParameter("build", ApplicationInfo.getInstance().getBuild().asString());
    addUpdateRequestParameter("uid", getInstallationUID(PropertiesComponent.getInstance()));
    addUpdateRequestParameter("os", SystemInfo.OS_NAME + ' ' + SystemInfo.OS_VERSION);
    if (ApplicationInfoEx.getInstanceEx().isEAP()) {
      addUpdateRequestParameter("eap", "");
    }

    for (String name : ourAdditionalRequestOptions.keySet()) {
      String value = ourAdditionalRequestOptions.get(name);
      uriBuilder.addParameter(name, StringUtil.isEmpty(value) ? null : value);
    }
  }

  public static String getInstallationUID(final PropertiesComponent propertiesComponent) {
    if (SystemInfo.isWindows) {
      String uid = getInstallationUIDOnWindows(propertiesComponent);
      if (uid != null) {
        return uid;
      }
    }

    String uid = propertiesComponent.getValue(INSTALLATION_UID);
    if (uid == null) {
      uid = generateUUID();
      propertiesComponent.setValue(INSTALLATION_UID, uid);
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

          String uuid = propertiesComponent.getValue(INSTALLATION_UID);
          if (uuid == null) {
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

  public static void installPlatformUpdate(final PatchInfo patch, final BuildNumber toBuild, final boolean forceHttps) throws IOException {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new ThrowableComputable<Void, IOException>() {
      @Override
      public Void compute() throws IOException {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        downloadAndInstallPatch(patch, toBuild, forceHttps, indicator);
        return null;
      }
    }, IdeBundle.message("update.downloading.patch.progress.title"), true, null);
  }

  private static void downloadAndInstallPatch(PatchInfo patch, BuildNumber toBuild, boolean forceHttps, final ProgressIndicator indicator) throws IOException {
    String productCode = ApplicationInfo.getInstance().getBuild().getProductCode();
    String fromBuildNumber = patch.getFromBuild().asStringWithoutProductCode();
    String toBuildNumber = toBuild.asStringWithoutProductCode();

    String bundledJdk = "";
    String jdkMacRedist = System.getProperty("idea.java.redist");
    if (jdkMacRedist != null && jdkMacRedist.lastIndexOf("jdk-bundled") >= 0) {
      bundledJdk = "jdk-bundled".equals(jdkMacRedist) ? "-jdk-bundled" : "-custom-jdk-bundled";
    }

    String osSuffix = "-" + patch.getOSSuffix();

    String fileName = productCode + "-" + fromBuildNumber + "-" + toBuildNumber + "-patch" + bundledJdk + osSuffix + ".jar";

    String url = new URL(new URL(getPatchesUrl()), fileName).toString();
    File tempFile = HttpRequests.request(url).gzip(false).forceHttps(forceHttps).connect(new HttpRequests.RequestProcessor<File>() {
      @Override
      public File process(@NotNull HttpRequests.Request request) throws IOException {
        return request.saveToFile(FileUtil.createTempFile("ij.platform.", ".patch", true), indicator);
      }
    });

    String patchFileName = ("jetbrains.patch.jar." + PlatformUtils.getPlatformPrefix()).toLowerCase(Locale.ENGLISH);
    File patchFile = new File(FileUtil.getTempDirectory(), patchFileName);
    FileUtil.copy(tempFile, patchFile);
    FileUtil.delete(tempFile);
  }

  public static boolean installPluginUpdates(@NotNull Collection<PluginDownloader> downloaders, @NotNull ProgressIndicator indicator) {
    boolean installed = false;

    Set<String> disabledToUpdate = getDisabledToUpdatePlugins();
    for (PluginDownloader downloader : downloaders) {
      if (disabledToUpdate.contains(downloader.getPluginId())) {
        continue;
      }
      try {
        if (downloader.prepareToInstall(indicator)) {
          IdeaPluginDescriptor descriptor = downloader.getDescriptor();
          if (descriptor != null) {
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
    if (!ourHasFailedPlugins && UpdateSettings.getInstance().isCheckNeeded()) {
      final Throwable throwable = event.getThrowable();
      final IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(IdeErrorsDialog.findPluginId(throwable));
      if (pluginDescriptor != null && !pluginDescriptor.isBundled()) {
        ourHasFailedPlugins = true;
        updateAndShowResult();
      }
    }
  }
}
