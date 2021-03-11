// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.WhatsNewAction;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.max;

final class UpdateCheckerComponent {
  public static UpdateCheckerComponent getInstance() {
    return ApplicationManager.getApplication().getService(UpdateCheckerComponent.class);
  }

  static final String SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY = "ide.self.update.started.for.build";

  private static final Logger LOG = Logger.getInstance(UpdateCheckerComponent.class);

  private static final long CHECK_INTERVAL = DateFormatUtil.DAY;
  private static final String ERROR_LOG_FILE_NAME = "idea_updater_error.log"; // must be equal to com.intellij.updater.Runner.ERROR_LOG_FILE_NAME
  private static final String PREVIOUS_BUILD_NUMBER_PROPERTY = "ide.updates.previous.build.number";
  private static final String WHATS_NEW_SHOWN_FOR_PROPERTY = "ide.updates.whats.new.shown.for";

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
        UpdateChecker.getNotificationGroup().createNotification(title, message, NotificationType.INFORMATION, null, "ide.update.channel.switched").notify(null);
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
    private static final AtomicBoolean ourWaiting = new AtomicBoolean(true);

    MyActivity() {
      Application app = ApplicationManager.getApplication();
      if (app.isCommandLine() || app.isHeadlessEnvironment() || app.isUnitTestMode()) throw ExtensionNotApplicableException.INSTANCE;
    }

    @Override
    public void runActivity(@NotNull Project project) {
      if (ourWaiting.getAndSet(false)) {
        checkIfPreviousUpdateFailed();

        PropertiesComponent properties = PropertiesComponent.getInstance();
        BuildNumber previous = BuildNumber.fromString(properties.getValue(PREVIOUS_BUILD_NUMBER_PROPERTY));
        BuildNumber current = ApplicationInfo.getInstance().getBuild();
        properties.setValue(PREVIOUS_BUILD_NUMBER_PROPERTY, current.asString());
        showWhatsNew(project, previous, current);
        showSnapUpdateNotification(project, previous, current);

        showUpdatedPluginsNotification(project);

        ProcessIOExecutorService.INSTANCE.execute(() -> UpdateInstaller.cleanupPatch());
      }
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

    Product product = UpdateChecker.getProductData().first;
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
    Product product = UpdateChecker.getProductData().first;
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
      .createNotification(title, message, NotificationType.INFORMATION, NotificationListener.URL_OPENING_LISTENER, "ide.updated.by.snap")
      .notify(project);
  }

  private static void showUpdatedPluginsNotification(Project project) {
    ApplicationManager.getApplication().getMessageBus().connect().subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appWillBeClosed(boolean isRestart) {
        Collection<PluginId> plugins = InstalledPluginsState.getInstance().getUpdatedPlugins();
        if (plugins.isEmpty()) {
          return;
        }

        Set<String> list = getUpdatedPlugins();
        for (PluginId plugin : plugins) {
          list.add(plugin.getIdString());
        }

        try {
          Files.write(getUpdatedPluginsFile(), list);
        }
        catch (IOException e) {
          LOG.warn(e);
        }
      }
    });

    Set<String> list = getUpdatedPlugins();
    if (list.isEmpty()) {
      return;
    }

    List<IdeaPluginDescriptor> descriptors = new ArrayList<>();
    for (String id : list) {
      PluginId pluginId = PluginId.findId(id);
      if (pluginId != null) {
        IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(pluginId);
        if (descriptor != null) {
          descriptors.add(descriptor);
        }
      }
    }
    if (descriptors.isEmpty()) {
      return;
    }

    String title = IdeBundle.message("update.installed.notification.title");
    String message = new HtmlBuilder()
      .appendWithSeparators(HtmlChunk.text(", "),
                            ContainerUtil.map(descriptors, d -> HtmlChunk.link(d.getPluginId().getIdString(), d.getName())))
      .wrapWith("html").toString();

    UpdateChecker.getNotificationGroupForUpdateResults()
      .createNotification(title,
                          message,
                          NotificationType.INFORMATION,
                          (__, event) -> {
                            String id = event.getDescription();
                            PluginId pluginId = id != null ? PluginId.findId(id) : null;

                            if (pluginId != null) {
                              PluginManagerConfigurable.showPluginConfigurable(findProject(event), List.of(pluginId));
                            }
                          }, "plugins.updated.after.restart").notify(project);
  }

  private static @Nullable Project findProject(@NotNull HyperlinkEvent event) {
    InputEvent inputEvent = event.getInputEvent();
    JComponent component = inputEvent != null ? (JComponent)inputEvent.getComponent() : null;
    DataProvider provider = component != null ? DataManager.getDataProvider(component) : null;
    return provider != null ? CommonDataKeys.PROJECT.getData(provider) : null;
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
    return Paths.get(PathManager.getConfigPath(), ".updated_plugins_list");
  }
}
