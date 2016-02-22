/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.updateSettings.UpdateStrategyCustomization;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiser;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.Alarm;
import com.intellij.util.text.DateFormatUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * @author yole
 */
public class UpdateCheckerComponent implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(UpdateCheckerComponent.class);

  private static final long CHECK_INTERVAL = DateFormatUtil.DAY;

  private final Alarm myCheckForUpdatesAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Runnable myCheckRunnable = new Runnable() {
    @Override
    public void run() {
      UpdateChecker.updateAndShowResult().doWhenDone(new Runnable() {
        @Override
        public void run() {
          queueNextCheck(CHECK_INTERVAL);
        }
      });
    }
  };
  private final UpdateSettings mySettings;

  public UpdateCheckerComponent(@NotNull Application app, @NotNull UpdateSettings settings) {
    mySettings = settings;
    updateDefaultChannel(app);
    checkSecureConnection(app);
    scheduleOnStartCheck(app);
  }

  private void updateDefaultChannel(Application app) {
    ChannelStatus current = mySettings.getSelectedChannelStatus();
    LOG.info("channel: " + current.getCode());
    boolean eap = ApplicationInfoEx.getInstanceEx().isEAP();

    if (eap && current != ChannelStatus.EAP && UpdateStrategyCustomization.getInstance().forceEapUpdateChannelForEapBuilds()) {
      mySettings.setSelectedChannelStatus(ChannelStatus.EAP);
      LOG.info("channel forced to 'eap'");
      if (!ConfigImportHelper.isFirstSession()) {
        String title = IdeBundle.message("update.notifications.title");
        String message = IdeBundle.message("update.channel.enforced", ChannelStatus.EAP);
        notify(app, UpdateChecker.NOTIFICATIONS.createNotification(title, message, NotificationType.INFORMATION, null));
      }
    }

    if (!eap && current == ChannelStatus.EAP && ConfigImportHelper.isConfigImported()) {
      mySettings.setSelectedChannelStatus(ChannelStatus.RELEASE);
      LOG.info("channel set to 'release'");
    }
  }

  private void checkSecureConnection(final Application app) {
    if (mySettings.isSecureConnection() && !mySettings.canUseSecureConnection()) {
      mySettings.setSecureConnection(false);

      boolean tooOld = !SystemInfo.isJavaVersionAtLeast("1.7");
      String title = IdeBundle.message("update.notifications.title");
      String message = IdeBundle.message(tooOld ? "update.sni.not.available.message" : "update.sni.disabled.message");
      notify(app, UpdateChecker.NOTIFICATIONS.createNotification(title, message, NotificationType.WARNING, new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification1, @NotNull HyperlinkEvent e) {
          notification1.expire();
          app.invokeLater(new Runnable() {
            @Override
            public void run() {
              ShowSettingsUtil.getInstance().showSettingsDialog(null, UpdateSettingsConfigurable.class);
            }
          }, ModalityState.NON_MODAL);
        }
      }));
    }
  }

  private void scheduleOnStartCheck(Application app) {
    if (!mySettings.isCheckNeeded()) {
      return;
    }

    app.getMessageBus().connect(app).subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener.Adapter() {
      @Override
      public void appFrameCreated(String[] commandLineArgs, @NotNull Ref<Boolean> willOpenProject) {
        BuildNumber currentBuild = ApplicationInfo.getInstance().getBuild();
        BuildNumber lastBuildChecked = BuildNumber.fromString(mySettings.getLasBuildChecked());
        long timeToNextCheck = mySettings.getLastTimeChecked() + CHECK_INTERVAL - System.currentTimeMillis();

        if (lastBuildChecked == null || currentBuild.compareTo(lastBuildChecked) > 0 || timeToNextCheck <= 0) {
          myCheckRunnable.run();
        }
        else {
          queueNextCheck(timeToNextCheck);
        }
      }
    });
  }

  private void queueNextCheck(long interval) {
    myCheckForUpdatesAlarm.addRequest(myCheckRunnable, interval);
  }

  private static void notify(Application app, final Notification notification) {
    app.invokeLater(new Runnable() {
      @Override
      public void run() {
        notification.notify(null);
      }
    }, ModalityState.NON_MODAL);
  }

  @Override
  public void initComponent() {
    PluginsAdvertiser.ensureDeleted();
  }

  @Override
  public void disposeComponent() {
    Disposer.dispose(myCheckForUpdatesAlarm);
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "UpdateCheckerComponent";
  }

  public void queueNextCheck() {
    queueNextCheck(CHECK_INTERVAL);
  }

  public void cancelChecks() {
    myCheckForUpdatesAlarm.cancelAllRequests();
  }
}