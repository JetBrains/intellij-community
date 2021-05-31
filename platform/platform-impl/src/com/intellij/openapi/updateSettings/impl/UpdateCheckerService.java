// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.WhatsNewAction;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.max;

final class UpdateCheckerService {
  public static UpdateCheckerService getInstance() {
    return ApplicationManager.getApplication().getService(UpdateCheckerService.class);
  }

  static final String SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY = "ide.self.update.started.for.build";

  private static final Logger LOG = Logger.getInstance(UpdateCheckerService.class);

  private static final long CHECK_INTERVAL = DateFormatUtil.DAY;
  private static final String ERROR_LOG_FILE_NAME = "idea_updater_error.log"; // must be equal to com.intellij.updater.Runner.ERROR_LOG_FILE_NAME
  private static final String PREVIOUS_BUILD_NUMBER_PROPERTY = "ide.updates.previous.build.number";
  private static final String WHATS_NEW_SHOWN_FOR_PROPERTY = "ide.updates.whats.new.shown.for";
  private static final String OLD_DIRECTORIES_SCAN_SCHEDULED = "ide.updates.old.dirs.scan.scheduled";
  private static final int OLD_DIRECTORIES_SCAN_DELAY_DAYS = 7;
  private static final int OLD_DIRECTORIES_SHELF_LIFE_DAYS = 180;

  private volatile ScheduledFuture<?> myScheduledCheck;

  static final class MyAppLifecycleListener implements AppLifecycleListener {
    @Override
    public void appStarted() {
      Application app = ApplicationManager.getApplication();
      if (!(app.isCommandLine() || app.isHeadlessEnvironment() || app.isUnitTestMode())) {
        getInstance().appStarted();
      }
    }
  }

  public void queueNextCheck() {
    queueNextCheck(CHECK_INTERVAL);
  }

  public void cancelChecks() {
    ScheduledFuture<?> future = myScheduledCheck;
    if (future != null) future.cancel(false);
  }

  private void appStarted() {
    UpdateSettings settings = UpdateSettings.getInstance();
    updateDefaultChannel(settings);
    if (settings.isCheckNeeded() || settings.isPluginsCheckNeeded()) {
      scheduleFirstCheck(settings);
    }
  }

  private static void updateDefaultChannel(UpdateSettings settings) {
    ChannelStatus current = settings.getSelectedChannelStatus();
    LOG.info("channel: " + current.getCode());
    boolean eap = ApplicationInfoEx.getInstanceEx().isMajorEAP();

    if (eap && current != ChannelStatus.EAP && UpdateStrategyCustomization.getInstance().forceEapUpdateChannelForEapBuilds()) {
      settings.setSelectedChannelStatus(ChannelStatus.EAP);
      LOG.info("channel forced to 'eap'");
      if (!ConfigImportHelper.isFirstSession()) {
        String title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
        String message = IdeBundle.message("update.channel.enforced", ChannelStatus.EAP);
        UpdateChecker.getNotificationGroup()
          .createNotification(title, message, NotificationType.INFORMATION)
          .setDisplayId("ide.update.channel.switched")
          .notify(null);
      }
    }

    if (!eap && current == ChannelStatus.EAP && ConfigImportHelper.isConfigImported()) {
      settings.setSelectedChannelStatus(ChannelStatus.RELEASE);
      LOG.info("channel set to 'release'");
    }
  }

  private void scheduleFirstCheck(UpdateSettings settings) {
    BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
    BuildNumber lastBuildChecked = BuildNumber.fromString(settings.getLastBuildChecked());
    long timeSinceLastCheck = max(System.currentTimeMillis() - settings.getLastTimeChecked(), 0);

    if (lastBuildChecked == null || currentBuild.compareTo(lastBuildChecked) > 0 || timeSinceLastCheck >= CHECK_INTERVAL) {
      checkUpdates();
    }
    else {
      queueNextCheck(CHECK_INTERVAL - timeSinceLastCheck);
    }
  }

  private void queueNextCheck(long delay) {
    myScheduledCheck = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> checkUpdates(), delay, TimeUnit.MILLISECONDS);
  }

  private void checkUpdates() {
    UpdateSettings settings = UpdateSettings.getInstance();
    if (settings.isCheckNeeded() || settings.isPluginsCheckNeeded()) {
      UpdateChecker.updateAndShowResult().doWhenProcessed(() -> queueNextCheck());
    }
  }

  static final class MyActivity implements StartupActivity.DumbAware {
    private static final AtomicBoolean ourStarted = new AtomicBoolean(false);

    MyActivity() {
      Application app = ApplicationManager.getApplication();
      if (app.isCommandLine() || app.isHeadlessEnvironment() || app.isUnitTestMode()) {
        throw ExtensionNotApplicableException.INSTANCE;
      }
    }

    @Override
    public void runActivity(@NotNull Project project) {
      if (ourStarted.getAndSet(true)) {
        return;
      }

      checkIfPreviousUpdateFailed();

      PropertiesComponent properties = PropertiesComponent.getInstance();
      BuildNumber previous = BuildNumber.fromString(properties.getValue(PREVIOUS_BUILD_NUMBER_PROPERTY));
      BuildNumber current = ApplicationInfo.getInstance().getBuild();
      properties.setValue(PREVIOUS_BUILD_NUMBER_PROPERTY, current.asString());
      showWhatsNew(project, previous, current);
      showSnapUpdateNotification(project, previous, current);

      showUpdatedPluginsNotification(project);

      ProcessIOExecutorService.INSTANCE.execute(() -> UpdateInstaller.cleanupPatch());

      deleteOldApplicationDirectories();
    }
  }

  private static void checkIfPreviousUpdateFailed() {
    PropertiesComponent properties = PropertiesComponent.getInstance();
    if (ApplicationInfo.getInstance().getBuild().asString().equals(properties.getValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY)) &&
        new File(PathManager.getLogPath(), ERROR_LOG_FILE_NAME).length() > 0) {
      IdeUpdateUsageTriggerCollector.trigger("update.failed");
      LOG.info("The previous IDE update failed");
    }
    properties.unsetValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY);
  }

  private static void showWhatsNew(Project project, @Nullable BuildNumber previous, BuildNumber current) {
    if (!WhatsNewAction.isAvailable() || !UpdateSettings.getInstance().isShowWhatsNewEditor()) return;

    if (previous != null && previous.getBaselineVersion() > current.getBaselineVersion()) return;  // a downgrade

    int shownFor = PropertiesComponent.getInstance().getInt(WHATS_NEW_SHOWN_FOR_PROPERTY, 0);
    if (shownFor == current.getBaselineVersion()) return;  // already shown for this release

    String url = ApplicationInfoEx.getInstanceEx().getWhatsNewUrl();
    if (url == null) return;

    Product product = loadProductData();
    if (product == null) return;

    int lastRelease = 0;
    String announce = null;
    String releaseVersion = ApplicationInfo.getInstance().getShortVersion();
    for (UpdateChannel updateChannel : product.getChannels()) {
      if (updateChannel.getLicensing() == UpdateChannel.Licensing.RELEASE && updateChannel.getStatus() == ChannelStatus.RELEASE) {
        for (BuildInfo buildInfo : updateChannel.getBuilds()) {
          lastRelease = max(lastRelease, buildInfo.getNumber().getBaselineVersion());
          announce = releaseVersion.equals(buildInfo.getVersion()) && announce == null ? buildInfo.getMessage() : announce;
        }
      }
    }
    if (lastRelease < current.getBaselineVersion()) return;  // not yet released
    if (lastRelease > current.getBaselineVersion()) url = null;  // "what's new" page is no longer relevant to this release

    PropertiesComponent.getInstance().setValue(WHATS_NEW_SHOWN_FOR_PROPERTY, current.getBaselineVersion(), 0);
    if (url != null || announce != null) {
      String _url = url, _announce = announce;
      ApplicationManager.getApplication().invokeLater(() -> WhatsNewAction.openWhatsNewFile(project, _url, _announce));
      IdeUpdateUsageTriggerCollector.trigger("update.whats.new");
    }
    else {
      LOG.info("neither URL nor message available for " + current);
    }
  }

  private static void showSnapUpdateNotification(Project project, @Nullable BuildNumber previous, BuildNumber current) {
    if (ExternalUpdateManager.ACTUAL != ExternalUpdateManager.SNAP || previous == null || current.equals(previous)) return;

    String blogPost = null;
    Product product = loadProductData();
    if (product != null) {
      blogPost = product.getChannels().stream()
        .flatMap(channel -> channel.getBuilds().stream())
        .filter(build -> current.equals(build.getNumber()))
        .findFirst().map(BuildInfo::getBlogPost).orElse(null);
    }

    String title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
    String message = blogPost == null ? IdeBundle.message("update.snap.message")
                                      : IdeBundle.message("update.snap.message.with.blog.post", StringUtil.escapeXmlEntities(blogPost));
    UpdateChecker.getNotificationGroup()
      .createNotification(title, message, NotificationType.INFORMATION)
      .setListener(NotificationListener.URL_OPENING_LISTENER)
      .setDisplayId("ide.updated.by.snap")
      .notify(project);
  }

  private static @Nullable Product loadProductData() {
    try {
      return UpdateChecker.loadProductData(null);
    }
    catch (Exception ignored) {
      return null;
    }
  }

  private static void showUpdatedPluginsNotification(Project project) {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appWillBeClosed(boolean isRestart) {
        Collection<PluginId> plugins = InstalledPluginsState.getInstance().getUpdatedPlugins();
        if (plugins.isEmpty()) return;

        Set<String> idStrings = getUpdatedPlugins();
        for (PluginId plugin : plugins) {
          idStrings.add(plugin.getIdString());
        }
        try {
          Files.write(getUpdatedPluginsFile(), idStrings);
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }
    });

    List<HtmlChunk.Element> links = new ArrayList<>();
    for (String id : getUpdatedPlugins()) {
      PluginId pluginId = PluginId.findId(id);
      if (pluginId != null) {
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(pluginId);
        if (descriptor != null) {
          links.add(HtmlChunk.link(id, descriptor.getName()));
        }
      }
    }
    if (links.isEmpty()) return;

    String title = IdeBundle.message("update.installed.notification.title");
    String text = new HtmlBuilder().appendWithSeparators(HtmlChunk.text(", "), links).wrapWith("html").toString();
    UpdateChecker.getNotificationGroupForUpdateResults()
      .createNotification(title, text, NotificationType.INFORMATION)
      .setListener((__, e) -> showPluginConfigurable(e, project))  // benign leak - notifications are disposed on project close
      .setDisplayId("plugins.updated.after.restart")
      .notify(project);
  }

  private static void showPluginConfigurable(HyperlinkEvent event, Project project) {
    String id = event.getDescription();
    if (id != null) {
      PluginId pluginId = PluginId.findId(id);
      if (pluginId != null) {
        PluginManagerConfigurable.showPluginConfigurable(project, List.of(pluginId));
      }
    }
  }

  private static Set<String> getUpdatedPlugins() {
    try {
      Path file = getUpdatedPluginsFile();
      if (Files.isRegularFile(file)) {
        List<String> list = Files.readAllLines(file);
        Files.delete(file);
        return new HashSet<>(list);
      }
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return new HashSet<>();
  }

  private static Path getUpdatedPluginsFile() {
    return Path.of(PathManager.getConfigPath(), ".updated_plugins_list");
  }

  private static void deleteOldApplicationDirectories() {
    if (ConfigImportHelper.isConfigImported()) {
      long scheduledAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(OLD_DIRECTORIES_SCAN_DELAY_DAYS);
      LOG.info("scheduling old directories scan after " + DateFormatUtil.formatDateTime(scheduledAt));
      PropertiesComponent.getInstance().setValue(OLD_DIRECTORIES_SCAN_SCHEDULED, Long.toString(scheduledAt));
      OldDirectoryCleaner.Stats.scheduled();
    }
    else {
      long scheduledAt = PropertiesComponent.getInstance().getLong(OLD_DIRECTORIES_SCAN_SCHEDULED, 0L), now;
      if (scheduledAt != 0 && (now = System.currentTimeMillis()) >= scheduledAt) {
        OldDirectoryCleaner.Stats.started((int)TimeUnit.MILLISECONDS.toDays(now - scheduledAt) + OLD_DIRECTORIES_SCAN_DELAY_DAYS);
        LOG.info("starting old directories scan");
        long expireAfter = now - TimeUnit.DAYS.toMillis(OLD_DIRECTORIES_SHELF_LIFE_DAYS);
        ProcessIOExecutorService.INSTANCE.execute(() -> new OldDirectoryCleaner(expireAfter).seekAndDestroy(null, null));
        PropertiesComponent.getInstance().unsetValue(OLD_DIRECTORIES_SCAN_SCHEDULED);
        LOG.info("old directories scan complete");
      }
    }
  }
}
