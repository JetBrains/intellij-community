/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

import static java.lang.Math.max;

/**
 * @author yole
 */
public class UpdateCheckerComponent implements Disposable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(UpdateCheckerComponent.class);

  private static final long CHECK_INTERVAL = DateFormatUtil.DAY;

  private final Alarm myCheckForUpdatesAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Runnable myCheckRunnable = () -> UpdateChecker.updateAndShowResult().doWhenProcessed(() -> queueNextCheck(CHECK_INTERVAL));
  private final UpdateSettings mySettings;

  public UpdateCheckerComponent(@NotNull Application app, @NotNull UpdateSettings settings) {
    Disposer.register(this, myCheckForUpdatesAlarm);

    mySettings = settings;
    updateDefaultChannel();
    checkSecureConnection(app);
    scheduleOnStartCheck(app);
    cleanupPatch();
  }

  private void updateDefaultChannel() {
    ChannelStatus current = mySettings.getSelectedChannelStatus();
    LOG.info("channel: " + current.getCode());
    boolean eap = ApplicationInfoEx.getInstanceEx().isEAP();

    if (eap && current != ChannelStatus.EAP && UpdateStrategyCustomization.getInstance().forceEapUpdateChannelForEapBuilds()) {
      mySettings.setSelectedChannelStatus(ChannelStatus.EAP);
      LOG.info("channel forced to 'eap'");
      if (!ConfigImportHelper.isFirstSession()) {
        String title = IdeBundle.message("update.notifications.title");
        String message = IdeBundle.message("update.channel.enforced", ChannelStatus.EAP);
        UpdateChecker.NOTIFICATIONS.createNotification(title, message, NotificationType.INFORMATION, null).notify(null);
      }
    }

    if (!eap && current == ChannelStatus.EAP && ConfigImportHelper.isConfigImported()) {
      mySettings.setSelectedChannelStatus(ChannelStatus.RELEASE);
      LOG.info("channel set to 'release'");
    }
  }

  private void checkSecureConnection(Application app) {
    if (mySettings.isSecureConnection() && !mySettings.canUseSecureConnection()) {
      mySettings.setSecureConnection(false);

      String title = IdeBundle.message("update.notifications.title");
      String message = IdeBundle.message("update.sni.disabled.message");
      UpdateChecker.NOTIFICATIONS.createNotification(title, message, NotificationType.WARNING, new NotificationListener.Adapter() {
          @Override
          protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
            notification.expire();
            app.invokeLater(() -> ShowSettingsUtil.getInstance().showSettingsDialog(null, UpdateSettingsConfigurable.class), ModalityState.NON_MODAL);
          }
        }).notify(null);
    }
  }

  private void scheduleOnStartCheck(Application app) {
    if (!mySettings.isCheckNeeded()) {
      return;
    }

    app.getMessageBus().connect(app).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appFrameCreated(String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject) {
        BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
        BuildNumber lastBuildChecked = BuildNumber.fromString(mySettings.getLastBuildChecked());
        long timeSinceLastCheck = max(System.currentTimeMillis() - mySettings.getLastTimeChecked(), 0);

        if (lastBuildChecked == null || currentBuild.compareTo(lastBuildChecked) > 0 || timeSinceLastCheck >= CHECK_INTERVAL) {
          myCheckRunnable.run();
        }
        else {
          queueNextCheck(CHECK_INTERVAL - timeSinceLastCheck);
        }
      }
    });
  }

  private static void cleanupPatch() {
    new Task.Backgroundable(null, IdeBundle.message("update.cleaning.patch.progress"), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        UpdateInstaller.cleanupPatch();
      }
    }.queue();
  }

  private void queueNextCheck(long interval) {
    myCheckForUpdatesAlarm.addRequest(myCheckRunnable, interval);
  }

  @Override
  public void initComponent() {
    PluginsAdvertiser.ensureDeleted();
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
}