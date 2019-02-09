// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.EventLogExternalSettingsService;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.internal.statistic.service.fus.FUSWhitelist;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
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
    logProjectStates(project, EventLogExternalSettingsService.getInstance().getApprovedGroups(), false);
  }

  public void logApplicationStates() {
    logApplicationStates(EventLogExternalSettingsService.getInstance().getApprovedGroups(), false);
  }

  public void logProjectStates(@NotNull Project project, @NotNull FUSWhitelist whitelist, boolean recordAll) {
    if (!whitelist.isEmpty() || ApplicationManagerEx.getApplicationEx().isInternal()) {
      synchronized (LOCK) {
        for (ProjectUsagesCollector usagesCollector : ProjectUsagesCollector.getExtensions(this)) {
          if (recordAll || whitelist.accepts(usagesCollector.getGroupId(), usagesCollector.getVersion())) {
            final FeatureUsageGroup group = new FeatureUsageGroup(usagesCollector.getGroupId(), usagesCollector.getVersion());
            logUsagesAsStateEvents(project, group, usagesCollector.getData(project), usagesCollector.getUsages(project));
          }
        }
      }
    }
  }

  public void logApplicationStates(@NotNull FUSWhitelist whitelist, boolean recordAll) {
    synchronized (LOCK) {
      for (ApplicationUsagesCollector usagesCollector : ApplicationUsagesCollector.getExtensions(this)) {
        if (recordAll || whitelist.accepts(usagesCollector.getGroupId(), usagesCollector.getVersion())) {
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
      final FeatureUsageData groupData = addProject(project, context);
      for (UsageDescriptor usage : usages) {
        final FeatureUsageData data = mergeWithEventData(groupData, usage.getData(), usage.getValue());
        final Map<String, Object> eventData = data != null ? data.build() : Collections.emptyMap();
        logger.logState(group, usage.getKey(), eventData);
      }
    }
    logger.logState(group, INVOKED, new FeatureUsageData().addProject(project).build());
  }

  @Nullable
  private static FeatureUsageData addProject(@Nullable Project project,
                                             @Nullable FeatureUsageData context) {
    if (project == null && context == null) {
      return null;
    }
    return context != null ? context.addProject(project) : new FeatureUsageData().addProject(project);
  }

  @Nullable
  public static FeatureUsageData mergeWithEventData(@Nullable FeatureUsageData groupData, @Nullable FeatureUsageData data, int value) {
    if (data == null && value == 1) return groupData;

    final FeatureUsageData newData = groupData == null ? new FeatureUsageData() : groupData.copy();
    if (value != 1) {
      newData.addData("value", value);
    }

    if (data != null) {
      newData.merge(data, "event_");
    }
    return newData;
  }

  public static void logStateEvent(@NotNull FeatureUsageGroup group, @NotNull String event, @NotNull FeatureUsageData data) {
    FeatureUsageLogger.INSTANCE.logState(group, event, data.build());
    FeatureUsageLogger.INSTANCE.logState(group, INVOKED);
  }
}
