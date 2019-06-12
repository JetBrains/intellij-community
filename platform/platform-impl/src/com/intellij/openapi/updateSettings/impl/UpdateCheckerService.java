// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;

import static java.lang.Math.max;

/**
 * @author yole
 */
@Service
public final class UpdateCheckerService implements Disposable {
  public static UpdateCheckerService getInstance() {
    return ServiceManager.getService(UpdateCheckerService.class);
  }

  private static final Logger LOG = Logger.getInstance(UpdateCheckerService.class);

  private static final long CHECK_INTERVAL = DateFormatUtil.DAY;
  static final String SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY = "ide.self.update.started.for.build";
  private static final String ERROR_LOG_FILE_NAME = "idea_updater_error.log";
    //must be equal to com.intellij.updater.Runner.ERROR_LOG_FILE_NAME

  private final Alarm myCheckForUpdatesAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Runnable myCheckRunnable = () -> UpdateChecker.updateAndShowResult().doWhenProcessed(() -> queueNextCheck(CHECK_INTERVAL));

  public UpdateCheckerService() {
    Disposer.register(this, myCheckForUpdatesAlarm);
  }

  void checkForUpdates() {
    checkIfPreviousUpdateFailed();

    updateDefaultChannel();
    scheduleFirstCheck();
    cleanupPatch();
    snapPackageNotification();
  }

  private static void updateDefaultChannel() {
    UpdateSettings settings = UpdateSettings.getInstance();
    ChannelStatus current = settings.getSelectedChannelStatus();
    LOG.info("channel: " + current.getCode());
    boolean eap = ApplicationInfoEx.getInstanceEx().isMajorEAP();

    if (eap && current != ChannelStatus.EAP && UpdateStrategyCustomization.getInstance().forceEapUpdateChannelForEapBuilds()) {
      settings.setSelectedChannelStatus(ChannelStatus.EAP);
      LOG.info("channel forced to 'eap'");
      if (!ConfigImportHelper.isFirstSession()) {
        String title = IdeBundle.message("update.notifications.title");
        String message = IdeBundle.message("update.channel.enforced", ChannelStatus.EAP);
        UpdateChecker.NOTIFICATIONS.createNotification(title, message, NotificationType.INFORMATION, null).notify(null);
      }
    }

    if (!eap && current == ChannelStatus.EAP && ConfigImportHelper.isConfigImported()) {
      settings.setSelectedChannelStatus(ChannelStatus.RELEASE);
      LOG.info("channel set to 'release'");
    }
  }

  private void scheduleFirstCheck() {
    UpdateSettings settings = UpdateSettings.getInstance();
    if (!settings.isCheckNeeded()) {
      return;
    }

    BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
    BuildNumber lastBuildChecked = BuildNumber.fromString(settings.getLastBuildChecked());
    long timeSinceLastCheck = max(System.currentTimeMillis() - settings.getLastTimeChecked(), 0);

    if (lastBuildChecked == null || currentBuild.compareTo(lastBuildChecked) > 0 || timeSinceLastCheck >= CHECK_INTERVAL) {
      myCheckRunnable.run();
    }
    else {
      queueNextCheck(CHECK_INTERVAL - timeSinceLastCheck);
    }
  }

  private static void cleanupPatch() {
    ApplicationManager.getApplication().executeOnPooledThread(() -> UpdateInstaller.cleanupPatch());
  }

  private void queueNextCheck(long interval) {
    myCheckForUpdatesAlarm.addRequest(myCheckRunnable, interval);
  }

  private static void checkIfPreviousUpdateFailed() {
    PropertiesComponent properties = PropertiesComponent.getInstance();
    if (ApplicationInfo.getInstance().getBuild().asString().equals(properties.getValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY))) {
      File updateErrorsLog = new File(PathManager.getLogPath(), ERROR_LOG_FILE_NAME);
      try {
        if (updateErrorsLog.isFile() && !StringUtil.isEmptyOrSpaces(FileUtil.loadFile(updateErrorsLog))) {
          IdeUpdateUsageTriggerCollector.trigger("update.failed");
          LOG.info("Previous update of the IDE failed");
        }
      }
      catch (IOException ignored) {
      }
    }
    properties.setValue(SELF_UPDATE_STARTED_FOR_BUILD_PROPERTY, null);
  }

  @Override
  public void dispose() {
  }

  public void queueNextCheck() {
    queueNextCheck(CHECK_INTERVAL);
  }

  public void cancelChecks() {
    myCheckForUpdatesAlarm.cancelAllRequests();
  }

  private static void snapPackageNotification() {
    UpdateSettings settings = UpdateSettings.getInstance();
    if (!settings.isCheckNeeded() || ExternalUpdateManager.ACTUAL != ExternalUpdateManager.SNAP) {
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
      final BuildNumber lastBuildChecked = BuildNumber.fromString(settings.getLastBuildChecked());

      if (lastBuildChecked == null) {
        /* First IDE start, just save info about build */
        UpdateSettings.getInstance().saveLastCheckedInfo();
        return;
      }

      /* Show notification even in case of downgrade */
      if (!currentBuild.equals(lastBuildChecked)) {
        UpdatesInfo updatesInfo = null;
        try {
          updatesInfo = UpdateChecker.getUpdatesInfo();
        }
        catch (IOException | JDOMException e) {
          LOG.warn(e);
        }

        String blogPost = null;
        if (updatesInfo != null) {
          final Product product = updatesInfo.get(currentBuild.getProductCode());
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

        String message = ((blogPost == null) ? IdeBundle.message("update.snap.message")
                                             : IdeBundle
                            .message("update.snap.message.with.blog.post", StringUtil.escapeXmlEntities(blogPost)));

        UpdateChecker.NOTIFICATIONS.createNotification(IdeBundle.message("update.notifications.title"),
                                                       message,
                                                       NotificationType.INFORMATION,
                                                       NotificationListener.URL_OPENING_LISTENER).notify(null);

        UpdateSettings.getInstance().saveLastCheckedInfo();
      }
    });
  }
}