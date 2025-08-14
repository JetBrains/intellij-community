// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.WhatsNewAction;
import com.intellij.ide.actions.WhatsNewUtil;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.AppMode;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.*;

@ApiStatus.Internal
public class UpdateCheckerService {
  public static UpdateCheckerService getInstance() {
    return ApplicationManager.getApplication().getService(UpdateCheckerService.class);
  }

  static final String SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY = "ide.self.update.started.for.build";

  private static final Logger LOG = Logger.getInstance(UpdateCheckerService.class);

  private static final long CHECK_INTERVAL_MS = MINUTES.toMillis(Long.getLong("ide.updates.check.interval.minutes", DAYS.toMinutes(1)));
  private static final String ERROR_LOG_FILE_NAME = "idea_updater_error.log"; // must be equal to 'com.intellij.updater.Runner.ERROR_LOG_FILE_NAME'
  private static final String PREVIOUS_BUILD_NUMBER_PROPERTY = "ide.updates.previous.build.number";
  private static final String OLD_DIRECTORIES_SCAN_SCHEDULED = "ide.updates.old.dirs.scan.scheduled";
  private static final int OLD_DIRECTORIES_SCAN_DELAY_DAYS = 7;
  private static final int OLD_DIRECTORIES_SHELF_LIFE_DAYS = 180;

  private volatile ScheduledFuture<?> myScheduledCheck;

  static final class MyAppLifecycleListener implements AppLifecycleListener {
    @Override
    public void appStarted() {
      var app = ApplicationManager.getApplication();
      if (!(app.isCommandLine() || app.isHeadlessEnvironment() || app.isUnitTestMode() || AppMode.isRemoteDevHost())) {
        getInstance().appStarted();
      }
    }
  }

  public void queueNextCheck() {
    queueNextCheck(CHECK_INTERVAL_MS);
  }

  public void cancelChecks() {
    var future = myScheduledCheck;
    if (future != null) {
      future.cancel(false);
    }
  }

  private void appStarted() {
    var settings = UpdateSettings.getInstance();
    updateDefaultChannel(settings);
    if (settings.isCheckNeeded() || settings.isPluginsCheckNeeded()) {
      scheduleFirstCheck(settings);
    }
  }

  private static void updateDefaultChannel(UpdateSettings settings) {
    var current = settings.getSelectedChannelStatus();
    LOG.info("channel: " + current.getCode());

    var customization = UpdateStrategyCustomization.getInstance();
    var changedChannel = customization.changeDefaultChannel(current);
    if (changedChannel != null) {
      settings.setSelectedChannelStatus(changedChannel);
      LOG.info("channel set to '" + changedChannel.getCode() + "' by " + customization.getClass().getName());
      return;
    }

    var appInfo = ApplicationInfoEx.getInstanceEx();

    if (appInfo.isMajorEAP() && current != ChannelStatus.EAP && customization.forceEapUpdateChannelForEapBuilds()) {
      settings.setSelectedChannelStatus(ChannelStatus.EAP);
      LOG.info("channel forced to 'eap'");
      if (!ConfigImportHelper.isFirstSession()) {
        var title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
        var message = IdeBundle.message("update.channel.enforced", ChannelStatus.EAP);
        UpdateChecker.getNotificationGroup()
          .createNotification(title, message, NotificationType.INFORMATION)
          .setDisplayId("ide.update.channel.switched")
          .notify(null);
      }
    }

    if (!appInfo.isEAP() && !appInfo.isPreview() && current == ChannelStatus.EAP && ConfigImportHelper.isConfigImported()) {
      settings.setSelectedChannelStatus(ChannelStatus.RELEASE);
      LOG.info("channel set to 'release'");
    }
  }

  public void scheduleFirstCheck(UpdateSettings settings) {
    var currentBuild = ApplicationInfo.getInstance().getBuild();
    var lastBuildChecked = BuildNumber.fromString(settings.getLastBuildChecked());
    var timeSinceLastCheck = max(System.currentTimeMillis() - settings.getLastTimeChecked(), 0);

    if (lastBuildChecked == null || currentBuild.compareTo(lastBuildChecked) > 0 || timeSinceLastCheck >= CHECK_INTERVAL_MS) {
      checkUpdates();
    }
    else {
      queueNextCheck(CHECK_INTERVAL_MS - timeSinceLastCheck);
    }
  }

  private void queueNextCheck(long delay) {
    myScheduledCheck = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> checkUpdates(), delay, MILLISECONDS);
  }

  private void checkUpdates() {
    UpdateChecker.updateAndShowResult().doWhenProcessed(() -> {
      var settings = UpdateSettings.getInstance();
      if (settings.isCheckNeeded() || settings.isPluginsCheckNeeded()) {
        queueNextCheck();
      }
    });
  }

  static void checkIfPreviousUpdateFailed(BuildNumber current) {
    var properties = PropertiesComponent.getInstance();
    if (
      current.asString().equals(properties.getValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY)) &&
      NioFiles.sizeIfExists(Path.of(PathManager.getLogPath(), ERROR_LOG_FILE_NAME)) > 0
    ) {
      IdeUpdateUsageTriggerCollector.UPDATE_FAILED.log();
      LOG.info("The previous IDE update failed");
    }
    properties.unsetValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY);
  }

  static void showWhatsNew(Project project, BuildNumber current) {
    var url = ExternalProductResourceUrls.getInstance().getWhatIsNewPageUrl();
    if (url != null && WhatsNewUtil.isWhatsNewAvailable() && shouldShowWhatsNew(current, ApplicationInfoEx.getInstanceEx().isMajorEAP())) {
      if (UpdateSettings.getInstance().isShowWhatsNewEditor()) {
        ApplicationManager.getApplication().invokeLater(
          () -> WhatsNewAction.openWhatsNewPage(project, url.toExternalForm(), true),
          project.getDisposed());
        IdeUpdateUsageTriggerCollector.majorUpdateHappened(true);
      }
      else {
        IdeUpdateUsageTriggerCollector.majorUpdateHappened(false);
      }
    }
  }

  @VisibleForTesting
  public static boolean shouldShowWhatsNew(@NotNull BuildNumber current, boolean majorEap) {
    if (ExperimentalUI.Companion.getForcedSwitchedUi()) {
      return false;
    }
    var settings = UpdateSettings.getInstance();

    var lastShownFor = settings.getWhatsNewShownFor();
    if (lastShownFor == 0) {
      // this ensures that the "what's new" page is shown _only_ for users who have updated from a previous version
      // (to detect updates, the method relies on imported settings; users starting from scratch are out of luck)
      settings.setWhatsNewShownFor(current.getBaselineVersion());
      return false;
    }

    if (!majorEap && lastShownFor < current.getBaselineVersion()) {
      var product = loadProductData();
      if (product != null) {
        // checking whether the actual "what's new" page is relevant to the current release
        var lastRelease = product.getChannels().stream()
          .filter(channel -> channel.getLicensing() == UpdateChannel.Licensing.RELEASE && channel.getStatus() == ChannelStatus.RELEASE)
          .flatMap(channel -> channel.getBuilds().stream())
          .mapToInt(build -> build.getNumber().getBaselineVersion())
          .max().orElse(0);
        if (lastRelease == current.getBaselineVersion()) {
          settings.setWhatsNewShownFor(current.getBaselineVersion());
          return true;
        }
      }
    }

    return false;
  }

  static void showSnapUpdateNotification(Project project, BuildNumber current) {
    if (ExternalUpdateManager.ACTUAL != ExternalUpdateManager.SNAP) return;

    var properties = PropertiesComponent.getInstance();
    var previous = BuildNumber.fromString(properties.getValue(PREVIOUS_BUILD_NUMBER_PROPERTY));
    properties.setValue(PREVIOUS_BUILD_NUMBER_PROPERTY, current.asString());
    if (previous == null || current.equals(previous)) return;

    String blogPost = null;
    var product = loadProductData();
    if (product != null) {
      blogPost = product.getChannels().stream()
        .flatMap(channel -> channel.getBuilds().stream())
        .filter(build -> current.equals(build.getNumber()))
        .findFirst().map(BuildInfo::getBlogPost).orElse(null);
    }

    var title = IdeBundle.message("updates.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
    var message = IdeBundle.message("update.snap.message");
    var notification = UpdateChecker.getNotificationGroupForIdeUpdateResults()
      .createNotification(title, message, NotificationType.INFORMATION)
      .setDisplayId("ide.updated.by.snap");
    if (blogPost != null) {
      var url = StringUtil.escapeXmlEntities(blogPost);
      notification.addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("update.snap.blog.post.action"), () -> BrowserUtil.browse(url)));
    }
    notification.notify(project);
  }

  private static @Nullable Product loadProductData() {
    try {
      return UpdateChecker.loadProductData(null);
    }
    catch (Exception ignored) {
      return null;
    }
  }

  static void showUpdatedPluginsNotification(Project project) {
    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appWillBeClosed(boolean isRestart) {
        var plugins = InstalledPluginsState.getInstance().getUpdatedPlugins();
        if (plugins.isEmpty()) return;

        var idStrings = getUpdatedPlugins();
        for (var plugin : plugins) {
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
    for (var id : getUpdatedPlugins()) {
      var pluginId = PluginId.getId(id);
      var descriptor = PluginManagerCore.getPlugin(pluginId);
      if (descriptor != null) {
        links.add(HtmlChunk.link(id, descriptor.getName()));
      }
    }
    if (links.isEmpty()) return;

    var title = IdeBundle.message("update.installed.notification.title");
    var text = new HtmlBuilder().appendWithSeparators(HtmlChunk.text(", "), links).wrapWith("html").toString();
    //noinspection deprecation
    UpdateChecker.getNotificationGroupForPluginUpdateResults()
      .createNotification(title, text, NotificationType.INFORMATION)
      .setListener((__, e) -> showPluginConfigurable(e, project))  // benign leak - notifications are disposed of on project close
      .setDisplayId("plugins.updated.after.restart")
      .notify(project);
  }

  private static void showPluginConfigurable(HyperlinkEvent event, Project project) {
    var id = event.getDescription();
    if (id != null) {
      var pluginId = PluginId.getId(id);
      PluginManagerConfigurable.showPluginConfigurable(project, List.of(pluginId));
    }
  }

  private static Set<String> getUpdatedPlugins() {
    try {
      var file = getUpdatedPluginsFile();
      if (Files.isRegularFile(file)) {
        var list = Files.readAllLines(file);
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

  static void deleteOldApplicationDirectories(@Nullable ProgressIndicator indicator) {
    var propertyService = PropertiesComponent.getInstance();
    if (ConfigImportHelper.isConfigImported()) {
      var scheduledAt = System.currentTimeMillis() + DAYS.toMillis(OLD_DIRECTORIES_SCAN_DELAY_DAYS);
      LOG.info("scheduling old directories scan after " + DateFormatUtil.formatDateTime(scheduledAt));
      propertyService.setValue(OLD_DIRECTORIES_SCAN_SCHEDULED, Long.toString(scheduledAt));
      OldDirectoryCleaner.Stats.scheduled();
    }
    else {
      long scheduledAt = propertyService.getLong(OLD_DIRECTORIES_SCAN_SCHEDULED, 0L), now;
      if (scheduledAt != 0 && (now = System.currentTimeMillis()) >= scheduledAt) {
        OldDirectoryCleaner.Stats.started((int)MILLISECONDS.toDays(now - scheduledAt) + OLD_DIRECTORIES_SCAN_DELAY_DAYS);
        LOG.info("starting old directories scan");
        var expireAfter = now - DAYS.toMillis(OLD_DIRECTORIES_SHELF_LIFE_DAYS);

        new OldDirectoryCleaner(expireAfter).seekAndDestroy(null, indicator);
        propertyService.unsetValue(OLD_DIRECTORIES_SCAN_SCHEDULED);
        LOG.info("old directories scan complete");
      }
    }
  }

  static void pruneUpdateSettings() {
    var settings = UpdateSettings.getInstance();

    if (settings.isObsoleteCustomRepositoriesCleanNeeded()) {
      var cleaned = settings.getStoredPluginHosts().removeIf(host -> host.startsWith("https://secure.feed.toolbox.app/plugins"));
      if (cleaned) {
        LOG.info("Some obsolete TBE custom repositories have been removed");
      }
      settings.setObsoleteCustomRepositoriesCleanNeeded(false);
    }

    var ignoredBuildNumbers = settings.getIgnoredBuildNumbers();
    if (!ignoredBuildNumbers.isEmpty()) {
      var currentBuild = ApplicationInfo.getInstance().getBuild();
      var cleaned = ignoredBuildNumbers.removeIf(str -> {
        var bn = BuildNumber.fromStringOrNull(str);
        return bn != null && currentBuild.compareTo(bn) >= 0;
      });
      if (cleaned) {
        LOG.info("Some obsolete ignored versions have been removed");
      }
    }
  }
}
