// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.utils.StatisticsUtilKt;
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
            logUsagesAsStateEvents(project, usagesCollector.getGroupId(), usagesCollector.getContext(project),
                                   usagesCollector.getUsages(project));
          }
        }
      }
    }
  }

  public void logApplicationStates(@NotNull Set<String> approvedGroups) {
    synchronized (LOCK) {
      for (ApplicationUsagesCollector usagesCollector : ApplicationUsagesCollector.getExtensions(this)) {
        if (approvedGroups.contains(usagesCollector.getGroupId())) {
          logUsagesAsStateEvents(null, usagesCollector.getGroupId(), usagesCollector.getContext(), usagesCollector.getUsages());
        }
      }
    }
  }

  private static void logUsagesAsStateEvents(@Nullable Project project,
                                             @NotNull String groupId,
                                             @Nullable FUSUsageContext context,
                                             @NotNull Set<UsageDescriptor> usages) {

    final FeatureUsageLogger logger = FeatureUsageLogger.INSTANCE;
    final FeatureUsageGroup group = new FeatureUsageGroup(groupId, 1);
    usages = usages.stream().filter(descriptor -> descriptor.getValue() > 0).collect(Collectors.toSet());
    if (!usages.isEmpty()) {
      final Map<String, ?> groupData = StatisticsUtilKt.createData(project, context);
      for (UsageDescriptor usage : usages) {
        final Map<String, Object> eventData = StatisticsUtilKt.mergeWithEventData(groupData, usage.getContext(), usage.getValue());
        logger.logState(group, usage.getKey(), eventData); // todo !!!
      }
    }
    logger.logState(group, INVOKED);
  }
}
