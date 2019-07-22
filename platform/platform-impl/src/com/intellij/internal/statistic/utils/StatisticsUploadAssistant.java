// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.FeatureUsageTrackerImpl;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.eventLog.EventLogStatisticsService;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Time;
import org.jetbrains.annotations.NotNull;

public class StatisticsUploadAssistant {
  private static final String IDEA_SUPPRESS_REPORT_STATISTICS = "idea.suppress.statistics.report";
  private static final String ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT = "idea.local.statistics.without.report";

  public static final Object LOCK = new Object();

  private StatisticsUploadAssistant(){}

  public static boolean isShouldShowNotification() {
    return UsageStatisticsPersistenceComponent.getInstance().isShowNotification() &&
           (System.currentTimeMillis() - Time.WEEK > ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getFirstRunTime());
  }

  public static long getSendPeriodInMillis() {
    return UsageStatisticsPersistenceComponent.getInstance().getPeriod().getMillis();
  }

  public static boolean isTimeToSend() {
    return isTimeToSend(UsageStatisticsPersistenceComponent.getInstance());
  }

  public static boolean isTimeToSend(UsageStatisticsPersistenceComponent settings) {
    final long timeDelta = System.currentTimeMillis() - settings.getLastTimeSent();

    return Math.abs(timeDelta) > settings.getPeriod().getMillis();
  }

  public static boolean isSendAllowed() {
    return isSendAllowed(UsageStatisticsPersistenceComponent.getInstance());
  }

  public static boolean isSendAllowed(final UsageStatisticsPersistenceComponent settings) {
    return settings != null && settings.isAllowed() &&
           !Boolean.getBoolean(IDEA_SUPPRESS_REPORT_STATISTICS) &&
           !Boolean.getBoolean(ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT);
  }

  public static boolean isCollectAllowed() {
    final UsageStatisticsPersistenceComponent settings = UsageStatisticsPersistenceComponent.getInstance();
    return (settings != null && settings.isAllowed()) || Boolean.getBoolean(ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT);
  }

  public static boolean isTestStatisticsEnabled() {
    return Boolean.getBoolean(ENABLE_LOCAL_STATISTICS_WITHOUT_REPORT) || StringUtil.isNotEmpty(System.getenv("TEAMCITY_VERSION"));
  }

  public static void updateSentTime() {
    UsageStatisticsPersistenceComponent.getInstance().setSentTime(System.currentTimeMillis());
  }

  @NotNull
  public static StatisticsService getEventLogStatisticsService(@NotNull String recorderId) {
    return new EventLogStatisticsService(recorderId);
  }
}
