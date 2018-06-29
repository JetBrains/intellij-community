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
package com.intellij.internal.statistic.utils;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.featureStatistics.FeatureUsageTrackerImpl;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.eventLog.EventLogStatisticsService;
import com.intellij.internal.statistic.persistence.SentUsagesPersistence;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.internal.statistic.service.fus.FUStatisticsService;
import com.intellij.util.Time;

public class StatisticsUploadAssistant {
  public static final Object LOCK = new Object();

  private StatisticsUploadAssistant(){};

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

  public static boolean isTimeToSendEventLog() {
    final long timeDelta = System.currentTimeMillis() - UsageStatisticsPersistenceComponent.getInstance().getEventLogLastTimeSent();
    return Math.abs(timeDelta) > UsageStatisticsPersistenceComponent.getInstance().getPeriod().getMillis();
  }

  public static boolean isSendAllowed() {
    return isSendAllowed(UsageStatisticsPersistenceComponent.getInstance());
  }

  public static boolean isSendAllowed(final SentUsagesPersistence settings) {
    return settings != null && settings.isAllowed();
  }

  public static void updateSentTime() {
    UsageStatisticsPersistenceComponent.getInstance().setSentTime(System.currentTimeMillis());
  }

  public static StatisticsService getApprovedGroupsStatisticsService() {
    return new FUStatisticsService();
  }

  public static StatisticsService getEventLogStatisticsService() {
    return new EventLogStatisticsService();
  }
}
