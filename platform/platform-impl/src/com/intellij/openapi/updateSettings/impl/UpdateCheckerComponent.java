// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.execution.process.ProcessIOExecutorService;
import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.InstalledPluginsState;
import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.impl.HTMLEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.LineSeparator;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.max;

final class UpdateCheckerComponent {
  public static UpdateCheckerComponent getInstance() {
    return ApplicationManager.getApplication().getService(UpdateCheckerComponent.class);
  }

  static final String SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY = "ide.self.update.started.for.build";
  static final String UPDATE_WHATS_NEW_MESSAGE = "ide.update.whats.new.message";

  private static final Logger LOG = Logger.getInstance(UpdateCheckerComponent.class);

  private static final long CHECK_INTERVAL = DateFormatUtil.DAY;
  private static final String ERROR_LOG_FILE_NAME = "idea_updater_error.log"; // must be equal to com.intellij.updater.Runner.ERROR_LOG_FILE_NAME

  private volatile ScheduledFuture<?> myScheduledCheck;

  static final class MyApplicationInitializedListener implements ApplicationInitializedListener {
    MyApplicationInitializedListener() {
      Application app = ApplicationManager.getApplication();
      if (app.isCommandLine() || app.isHeadlessEnvironment()) {
        throw ExtensionNotApplicableException.INSTANCE;
      }
    }

    @Override
    public void componentsInitialized() {
      UpdateSettings settings = UpdateSettings.getInstance();
      updateDefaultChannel(settings);
      if (settings.isCheckNeeded()) {
        scheduleFirstCheck(settings);
        snapPackageNotification(settings);
      }
    }
  }

  static final class MyActivity implements StartupActivity.DumbAware {
    private final @NotNull NotNullLazyValue<Boolean> updateFailed = AtomicNotNullLazyValue.createValue(() -> checkIfPreviousUpdateFailed());

    MyActivity() {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        throw ExtensionNotApplicableException.INSTANCE;
      }
    }

    @Override
    public void runActivity(@NotNull Project project) {
      if (Experiments.getInstance().isFeatureEnabled("whats.new.notification") && !updateFailed.getValue()) {
        showWhatsNewNotification(project);
      }

      showUpdatedPluginsNotification(project);
      ProcessIOExecutorService.INSTANCE.execute(() -> UpdateInstaller.cleanupPatch());
    }
  }

  public void queueNextCheck() {
    queueNextCheck(CHECK_INTERVAL);
  }

  public void cancelChecks() {
    ScheduledFuture<?> future = myScheduledCheck;
    if (future != null) future.cancel(false);
  }

  private static void showWhatsNewNotification(@NotNull Project project) {
    PropertiesComponent properties = PropertiesComponent.getInstance();
    String updateHtmlMessage = properties.getValue(UPDATE_WHATS_NEW_MESSAGE);
    if (updateHtmlMessage == null) {
      LOG.warn("Cannot show what's new notification: no content found.");
      return;
    }

    String title = IdeBundle.message("update.whats.new.notification.title", ApplicationNamesInfo.getInstance().getFullProductName());
    UpdateChecker.getNotificationGroup().createNotification(title, null, NotificationType.INFORMATION, null, "ide.update.installed")
      .addAction(new NotificationAction(IdeBundle.message("update.whats.new.notification.action")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          String title = IdeBundle.message("update.whats.new.file.name", ApplicationInfo.getInstance().getFullVersion());
          HTMLEditorProvider.Companion.openEditor(project, title, null, updateHtmlMessage, updateHtmlMessage);
          IdeUpdateUsageTriggerCollector.trigger("update.whats.new");
          notification.expire();
        }
      }).notify(project);
    properties.setValue(UPDATE_WHATS_NEW_MESSAGE, null);
  }

  private static boolean checkIfPreviousUpdateFailed() {
    PropertiesComponent properties = PropertiesComponent.getInstance();
    if (ApplicationInfo.getInstance().getBuild().asString().equals(properties.getValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY)) &&
        new File(PathManager.getLogPath(), ERROR_LOG_FILE_NAME).length() > 0) {
      IdeUpdateUsageTriggerCollector.trigger("update.failed");
      LOG.info("The previous IDE update failed");
      return false;
    }
    properties.setValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY, null);
    return true;
  }

  private static void updateDefaultChannel(@NotNull UpdateSettings settings) {
    ChannelStatus current = settings.getSelectedChannelStatus();
    LOG.info("channel: " + current.getCode());
    boolean eap = ApplicationInfoEx.getInstanceEx().isMajorEAP();

    if (eap && current != ChannelStatus.EAP && UpdateStrategyCustomization.getInstance().forceEapUpdateChannelForEapBuilds()) {
      settings.setSelectedChannelStatus(ChannelStatus.EAP);
      LOG.info("channel forced to 'eap'");
      if (!ConfigImportHelper.isFirstSession()) {
        String title = IdeBundle.message("update.notifications.title");
        String message = IdeBundle.message("update.channel.enforced", ChannelStatus.EAP);
        UpdateChecker.getNotificationGroup().createNotification(title, message, NotificationType.INFORMATION, null, "ide.update.channel.switched").notify(null);
      }
    }

    if (!eap && current == ChannelStatus.EAP && ConfigImportHelper.isConfigImported()) {
      settings.setSelectedChannelStatus(ChannelStatus.RELEASE);
      LOG.info("channel set to 'release'");
    }
  }

  private static void scheduleFirstCheck(@NotNull UpdateSettings settings) {
    BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
    BuildNumber lastBuildChecked = BuildNumber.fromString(settings.getLastBuildChecked());
    long timeSinceLastCheck = max(System.currentTimeMillis() - settings.getLastTimeChecked(), 0);

    if (lastBuildChecked == null || currentBuild.compareTo(lastBuildChecked) > 0 || timeSinceLastCheck >= CHECK_INTERVAL) {
      checkUpdates();
    }
    else {
      getInstance().queueNextCheck(CHECK_INTERVAL - timeSinceLastCheck);
    }
  }

  private void queueNextCheck(long delay) {
    myScheduledCheck = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
      checkUpdates();
    }, delay, TimeUnit.MILLISECONDS);
  }

  private static void checkUpdates() {
    UpdateChecker.updateAndShowResult().doWhenProcessed(() -> getInstance().queueNextCheck(CHECK_INTERVAL));
  }

  private static void snapPackageNotification(@NotNull UpdateSettings settings) {
    if (ExternalUpdateManager.ACTUAL != ExternalUpdateManager.SNAP) {
      return;
    }

    BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
    BuildNumber lastBuildChecked = BuildNumber.fromString(settings.getLastBuildChecked());
    if (lastBuildChecked == null) {
      // first IDE start, just save info about build
      UpdateSettings.getInstance().saveLastCheckedInfo();
      return;
    }

    // show notification even in case of downgrade
    if (currentBuild.equals(lastBuildChecked)) {
      return;
    }

    UpdatesInfo updatesInfo = null;
    try {
      updatesInfo = UpdateChecker.getUpdatesInfo();
    }
    catch (IOException | JDOMException e) {
      LOG.warn(e);
    }

    String blogPost = null;
    if (updatesInfo != null) {
      Product product = updatesInfo.get(currentBuild.getProductCode());
      if (product != null) {
        outer:
        for (UpdateChannel channel : product.getChannels()) {
          for (BuildInfo build : channel.getBuilds()) {
            if (currentBuild.equals(build.getNumber())) {
              blogPost = build.getBlogPost();
              break outer;
            }
          }
        }
      }
    }

    String title = IdeBundle.message("update.notifications.title");
    String message = blogPost == null ? IdeBundle.message("update.snap.message")
                                      : IdeBundle.message("update.snap.message.with.blog.post", StringUtil.escapeXmlEntities(blogPost));
    UpdateChecker.getNotificationGroup().createNotification(
      title, message, NotificationType.INFORMATION, NotificationListener.URL_OPENING_LISTENER, "ide.updated.by.snap").notify(null);

    UpdateSettings.getInstance().saveLastCheckedInfo();
  }

  private static void showUpdatedPluginsNotification(@NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

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
          FileUtil.writeToFile(getUpdatedPluginsFile(), StringUtil.join(list, LineSeparator.getSystemLineSeparator().getSeparatorString()));
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
    String message = "<html>" + StringUtil.join(descriptors, descriptor -> {
      return "<a href='" + descriptor.getPluginId().getIdString() + "'>" + descriptor.getName() + "</a>";
    }, ", ") + "</html>";

    UpdateChecker.getNotificationGroup().createNotification(title, message, NotificationType.INFORMATION, (notification, event) -> {
      String id = event.getDescription();
      if (id == null) {
        return;
      }

      PluginId pluginId = PluginId.findId(id);
      if (pluginId == null) {
        return;
      }

      IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(pluginId);
      if (descriptor == null) {
        return;
      }

      InputEvent inputEvent = event.getInputEvent();
      Component component = inputEvent == null ? null : inputEvent.getComponent();
      DataProvider provider = component == null ? null : DataManager.getDataProvider((JComponent)component);

      PluginManagerConfigurable.showPluginConfigurable(provider == null ? null : CommonDataKeys.PROJECT.getData(provider), descriptor);
    }, "plugins.updated.after.restart").notify(project);
  }

  @NotNull
  private static Set<String> getUpdatedPlugins() {
    try {
      File file = getUpdatedPluginsFile();
      if (file.isFile()) {
        List<String> list = FileUtil.loadLines(file);
        FileUtil.delete(file);
        return new HashSet<>(list);
      }
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return new HashSet<>();
  }

  @NotNull
  private static File getUpdatedPluginsFile() {
    return new File(PathManager.getConfigPath(), ".updated_plugins_list");
  }
}