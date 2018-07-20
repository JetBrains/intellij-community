package com.intellij.internal.statistic.updater;

import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.AppUIUtil;
import org.jetbrains.annotations.NotNull;

public class StatisticsNotificationManager {

  private StatisticsNotificationManager() {
  }

  public static void showNotification(@NotNull StatisticsService statisticsService) {
    if (AppUIUtil.showConsentsAgreementIfNeed()) {
      ApplicationManager.getApplication().executeOnPooledThread((Runnable)() -> statisticsService.send());
      UsageStatisticsPersistenceComponent.getInstance().setShowNotification(false);
    }
  }
}
