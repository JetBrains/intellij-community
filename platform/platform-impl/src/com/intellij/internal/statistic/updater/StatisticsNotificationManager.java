// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.updater;

import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.AppUIUtil;
import org.jetbrains.annotations.NotNull;

final class StatisticsNotificationManager {
  private StatisticsNotificationManager() {
  }

  public static void showNotification(@NotNull StatisticsService statisticsService) {
    if (AppUIUtil.showConsentsAgreementIfNeeded(Logger.getInstance(StatisticsNotificationManager.class))) {
      ApplicationManager.getApplication().executeOnPooledThread((Runnable)statisticsService::send);
      UsageStatisticsPersistenceComponent.getInstance().setShowNotification(false);
    }
  }
}
