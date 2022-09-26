// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.notification;

import com.intellij.application.Topics;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.FeatureUsageTrackerImpl;
import com.intellij.ide.StatisticsNotificationManager;
import com.intellij.ide.gdpr.ConsentOptions;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.BalloonLayoutImpl;
import com.intellij.util.Time;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

final class StatisticsNotificationManagerImpl implements StatisticsNotificationManager {
  @Override
  public void showNotificationIfNeeded() {
    if (!isShouldShowNotification()) {
      return;
    }

    NotificationsConfigurationImpl.remove("SendUsagesStatistics");

    Disposable disposable = Disposer.newDisposable();
    Topics.subscribe(ApplicationActivationListener.TOPIC, disposable, new ApplicationActivationListener() {
      @Override
      public void applicationActivated(@NotNull IdeFrame ideFrame) {
        if (isEmpty(WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow())) {
          ApplicationManager.getApplication().invokeLater(() -> showNotification());
          Disposer.dispose(disposable);
        }
      }
    });
  }

  private static boolean isShouldShowNotification() {
    return UsageStatisticsPersistenceComponent.getInstance().isShowNotification() &&
           (System.currentTimeMillis() - Time.WEEK > ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getFirstRunTime());
  }

  private static void showNotification() {
    if (AppUIUtil.showConsentsAgreementIfNeeded(Logger.getInstance(StatisticsNotificationManagerImpl.class), ConsentOptions.condUsageStatsConsent())) {
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        return StatisticsUploadAssistant.getEventLogStatisticsService("FUS").send();
      });
      UsageStatisticsPersistenceComponent.getInstance().setShowNotification(false);
    }
  }

  private static boolean isEmpty(@Nullable Window window) {
    ProjectFrameHelper frameHelper = ProjectFrameHelper.getFrameHelper(window);
    if (frameHelper != null) {
      BalloonLayout layout = frameHelper.getBalloonLayout();
      if (layout instanceof BalloonLayoutImpl) {
        // do not show notification if others exist
        return ((BalloonLayoutImpl)layout).isEmpty();
      }
    }
    return false;
  }
}
