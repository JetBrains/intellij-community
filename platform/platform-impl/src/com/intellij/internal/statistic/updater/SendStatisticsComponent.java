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
package com.intellij.internal.statistic.updater;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.FrameStateListener;
import com.intellij.ide.FrameStateManager;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.connect.StatisticsServiceEP;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.BalloonLayoutImpl;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class SendStatisticsComponent implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance(SendStatisticsComponent.class);

  private static final int DELAY_IN_MIN = 10;

  private final FrameStateManager myFrameStateManager;

  public SendStatisticsComponent(@NotNull FrameStateManager frameStateManager) {
    NotificationsConfigurationImpl.remove("SendUsagesStatistics");
    NotificationsConfiguration.getNotificationsConfiguration().register(
      StatisticsNotificationManager.GROUP_DISPLAY_ID,
      NotificationDisplayType.STICKY_BALLOON,
      false);

    myFrameStateManager = frameStateManager;
  }

  private static boolean isEmpty(Window window) {
    if (window instanceof IdeFrameImpl) {
      BalloonLayout layout = ((IdeFrameImpl)window).getBalloonLayout();
      if (layout instanceof BalloonLayoutImpl) {
        // do not show notification if others exist
        return ((BalloonLayoutImpl)layout).isEmpty();
      }
    }
    return false;
  }

  private void runStatisticsService() {
    final StatisticsService statisticsService = StatisticsUploadAssistant.getStatisticsService();

    if (StatisticsUploadAssistant.isShouldShowNotification()) {
      myFrameStateManager.addListener(new FrameStateListener.Adapter() {
        @Override
        public void onFrameActivated() {
          if (isEmpty(((WindowManagerEx)WindowManager.getInstance()).getMostRecentFocusedWindow())) {
            ApplicationManager.getApplication().invokeLater(() -> StatisticsNotificationManager.showNotification(statisticsService));
            myFrameStateManager.removeListener(this);
          }
        }
      });
    }
    else if (StatisticsUploadAssistant.isSendAllowed() && StatisticsUploadAssistant.isTimeToSend()) {
      runWithDelay(statisticsService);
    }
  }

  private static void runWithDelay(@NotNull final StatisticsService statisticsService) {
    JobScheduler.getScheduler().schedule(statisticsService::send, DELAY_IN_MIN, TimeUnit.MINUTES);
  }

  @Override
  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    runStatisticsService();
  }
}
