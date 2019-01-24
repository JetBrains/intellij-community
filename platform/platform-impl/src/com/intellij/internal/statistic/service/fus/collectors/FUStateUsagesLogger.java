// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.intellij.internal.statistic.utils.StatisticsUploadAssistant.LOCK;

public class FUStateUsagesLogger implements UsagesCollectorConsumer {
  /**
   * System event which indicates that the collector was called, can be used to calculate the base line
   */
  private static final String INVOKED = "invoked";

  public static FUStateUsagesLogger create() { return new FUStateUsagesLogger(); }

  public void logProjectStates(@NotNull Project project) {
    logProjectStates(project, EventLogExternalSettingsService.getInstance().getApprovedGroups());
  }

  public void logApplicationStates() {
    logApplicationStates(EventLogExternalSettingsService.getInstance().getApprovedGroups());
  }

  public void logProjectStates(@NotNull Project project, @NotNull Set<String> approvedGroups) {
    if (!approvedGroups.isEmpty() || ApplicationManagerEx.getApplicationEx().isInternal()) {
      synchronized (LOCK) {
        for (ProjectUsagesCollector usagesCollector : ProjectUsagesCollector.getExtensions(this)) {
          if (approvedGroups.contains(usagesCollector.getGroupId())) {
            final FeatureUsageGroup group = new FeatureUsageGroup(usagesCollector.getGroupId(), usagesCollector.getVersion());
            logUsagesAsStateEvents(project, group, usagesCollector.getData(project), usagesCollector.getUsages(project));
          }
        }
      }
    }
  }

  public void logApplicationStates(@NotNull Set<String> approvedGroups) {
    synchronized (LOCK) {
      for (ApplicationUsagesCollector usagesCollector : ApplicationUsagesCollector.getExtensions(this)) {
        if (approvedGroups.contains(usagesCollector.getGroupId())) {
          final FeatureUsageGroup group = new FeatureUsageGroup(usagesCollector.getGroupId(), usagesCollector.getVersion());
          logUsagesAsStateEvents(null, group, usagesCollector.getData(), usagesCollector.getUsages());
        }
      }
    }
  }

  private static void logUsagesAsStateEvents(@Nullable Project project,
                                             @NotNull FeatureUsageGroup group,
                                             @Nullable FeatureUsageData context,
                                             @NotNull Set<UsageDescriptor> usages) {
    final FeatureUsageLogger logger = FeatureUsageLogger.INSTANCE;
    usages = usages.stream().filter(descriptor -> descriptor.getValue() > 0).collect(Collectors.toSet());
    if (!usages.isEmpty()) {
      final FeatureUsageData groupData = context != null ? context.addProject(project) : new FeatureUsageData().addProject(project);
      for (UsageDescriptor usage : usages) {
        final FeatureUsageData data = mergeWithEventData(groupData, usage.getData(), usage.getValue());
        logger.logState(group, usage.getKey(), data.build());
      }
    }
    logger.logState(group, INVOKED);
  }

  @NotNull
  private static FeatureUsageData mergeWithEventData(@NotNull FeatureUsageData groupData, @Nullable FeatureUsageData data, int value) {
    if (data == null && value == 1) return groupData;

    final FeatureUsageData newData = groupData.copy();
    if (value != 1) {
      newData.addData("value", value);
    }

    if (data != null) {
      for (Map.Entry<String, Object> entry : data.build().entrySet()) {
        newData.addData("event_" + entry.getKey(), entry.getValue());
      }
    }
    return newData;
  }

  public static void logStateEvent(@NotNull FeatureUsageGroup group, @NotNull String event, @NotNull FeatureUsageData data) {
    FeatureUsageLogger.INSTANCE.logState(group, event, data.build());
    FeatureUsageLogger.INSTANCE.logState(group, INVOKED);
  }
}
