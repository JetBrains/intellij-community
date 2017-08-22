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
import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.connect.RemotelyConfigurableStatisticsService;
import com.intellij.internal.statistic.connect.StatisticsConnectionService;
import com.intellij.internal.statistic.connect.StatisticsHttpClientSender;
import com.intellij.internal.statistic.connect.StatisticsService;
import com.intellij.internal.statistic.persistence.SentUsagesPersistence;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.Time;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class StatisticsUploadAssistant {
  private static final Logger LOG = Logger.getInstance(UsagesCollector.class);
  public static final Object LOCK = new Object();

  public String getData() {
    return getData(Collections.emptySet());
  }

  public static boolean isShouldShowNotification() {
    return UsageStatisticsPersistenceComponent.getInstance().isShowNotification() &&
           (System.currentTimeMillis() - Time.WEEK > ((FeatureUsageTrackerImpl)FeatureUsageTracker.getInstance()).getFirstRunTime());
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

  public static boolean isSendAllowed(final SentUsagesPersistence settings) {
    return settings != null && settings.isAllowed();
  }

  public String getData(@NotNull Set<String> disabledGroups) {
    return getDataString(disabledGroups);
  }

  public static void updateSentTime() {
    UsageStatisticsPersistenceComponent.getInstance().setSentTime(System.currentTimeMillis());
  }

  @NotNull
  public static String getDataString(@NotNull Set<String> disabledGroups) {
    return getDataString(disabledGroups, 0);
  }

  @NotNull
  public static String getDataString(@NotNull Set<String> disabledGroups,
                                     int maxSize) {
    return getDataString(getAllUsages(disabledGroups), maxSize);
  }

  public static <T extends UsageDescriptor> String getDataString(@NotNull Map<GroupDescriptor, Set<T>> usages, int maxSize) {
    if (usages.isEmpty()) {
      return "";
    }

    String dataStr = ConvertUsagesUtil.convertUsages(usages);
    return maxSize > 0 && dataStr.getBytes(CharsetToolkit.UTF8_CHARSET).length > maxSize ? ConvertUsagesUtil.cutDataString(dataStr, maxSize) : dataStr;
  }

  private static final KeyedExtensionCollector<StatisticsService, String> COLLECTOR;

  static {
    COLLECTOR = new KeyedExtensionCollector<>("com.intellij.statisticsService");
  }

  public static StatisticsService getStatisticsService() {
    String key = ((ApplicationInfoImpl)ApplicationInfoImpl.getShadowInstance()).getStatisticsServiceKey();
    StatisticsService service = key == null ? null : COLLECTOR.findSingle(key);
    if (service != null) {
      return service;
    }

    return new RemotelyConfigurableStatisticsService(new StatisticsConnectionService(),
                                                     new StatisticsHttpClientSender(),
                                                     new StatisticsUploadAssistant());
  }

  @NotNull
  private static Map<GroupDescriptor, Set<UsageDescriptor>> getAllUsages(@NotNull Set<String> disabledGroups) {
    synchronized (LOCK) {
      Map<GroupDescriptor, Set<UsageDescriptor>> usageDescriptors = new LinkedHashMap<>();
      for (UsagesCollector usagesCollector : UsagesCollector.EP_NAME.getExtensions()) {
        GroupDescriptor groupDescriptor = usagesCollector.getGroupId();
        if (!disabledGroups.contains(groupDescriptor.getId())) {
          try {
            usageDescriptors.merge(groupDescriptor, usagesCollector.getUsages(), ContainerUtil::union);
          }
          catch (CollectUsagesException e) {
            LOG.info(e);
          }
        }
      }
      return usageDescriptors;
    }
  }
}
