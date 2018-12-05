// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.openapi.application.ex.ApplicationManagerEx;

import java.util.Set;

public class FUStatisticRecorder implements UsagesCollectorConsumer {
  private static final FUStatisticsAggregator myAggregator = FUStatisticsAggregator.create(true);
  private static final EventLogExternalSettingsService myEventLogSettingsService = EventLogExternalSettingsService.getInstance();

  public static void collect() {
    final String serviceUrl = myEventLogSettingsService.getServiceUrl();
    if (serviceUrl == null) {
      return;
    }

    final Set<String> approvedGroups = myEventLogSettingsService.getApprovedGroups();
    if (approvedGroups.isEmpty() && !ApplicationManagerEx.getApplicationEx().isInternal()) {
      return;
    }

    myAggregator.getUsageCollectorsData(approvedGroups);
  }
}
