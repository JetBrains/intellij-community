// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.intellij.internal.statistic.utils.StatisticsUploadAssistant.LOCK;

/**
 * <p>Called by a scheduler once a day and records IDE/project state.</p> <br/>
 *
 * <p><b>Don't</b> use it directly unless absolutely necessary.
 * Implement {@link ApplicationUsagesCollector} or {@link ProjectUsagesCollector} instead.</p>
 *
 * <p>To record IDE events (e.g. invoked action, opened dialog) use {@link FUCounterUsageLogger}</p>
 */
public class FUStateUsagesLogger implements UsagesCollectorConsumer {
  /**
   * System event which indicates that the collector was called.
   * Used to calculate metric baseline.
   */
  private static final String INVOKED = "invoked";

  public static FUStateUsagesLogger create() { return new FUStateUsagesLogger(); }

  public void logProjectStates(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    synchronized (LOCK) {
      for (ProjectUsagesCollector usagesCollector : ProjectUsagesCollector.getExtensions(this)) {
        final EventLogGroup group = new EventLogGroup(usagesCollector.getGroupId(), usagesCollector.getVersion());
        logUsagesAsStateEvents(project, group, usagesCollector.getData(project), usagesCollector.getMetrics(project, indicator));
      }
    }
  }

  public void logApplicationStates() {
    synchronized (LOCK) {
      for (ApplicationUsagesCollector usagesCollector : ApplicationUsagesCollector.getExtensions(this)) {
        final EventLogGroup group = new EventLogGroup(usagesCollector.getGroupId(), usagesCollector.getVersion());
        logUsagesAsStateEvents(null, group, usagesCollector.getData(), Promises.resolvedPromise(usagesCollector.getMetrics()));
      }
    }
  }

  private static void logUsagesAsStateEvents(@Nullable Project project,
                                             @NotNull EventLogGroup group,
                                             @Nullable FeatureUsageData context,
                                             @NotNull Promise<? extends Set<MetricEvent>> metricsPromise) {
    metricsPromise.onSuccess(metrics -> {
      if (project != null && project.isDisposed()) {
        return;
      }
      FeatureUsageLogger logger = FeatureUsageLogger.INSTANCE;
      if (!metrics.isEmpty()) {
        final FeatureUsageData groupData = addProject(project, context);
        for (MetricEvent metric : metrics) {
          final FeatureUsageData data = mergeWithEventData(groupData, metric.getData());
          final Map<String, Object> eventData = data != null ? data.build() : Collections.emptyMap();
          logger.logState(group, metric.getEventId(), eventData);
        }
      }
      logger.logState(group, INVOKED, new FeatureUsageData().addProject(project).build());
    });
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
  public static FeatureUsageData mergeWithEventData(@Nullable FeatureUsageData groupData, @Nullable FeatureUsageData data) {
    if (data == null) return groupData;

    final FeatureUsageData newData = groupData == null ? new FeatureUsageData() : groupData.copy();
    newData.merge(data, "event_");
    return newData;
  }

  /**
   * <p>
   * Low-level API to record IDE/project state.
   * Using it directly is error-prone because you'll need to think about metric baseline.
   * <b>Don't</b> use it unless absolutely necessary.
   * </p>
   * <br/>
   * <p>
   * Consider using counter events {@link FUCounterUsageLogger} or
   * state events recorded by a scheduler {@link ApplicationUsagesCollector} or {@link ProjectUsagesCollector}
   * </p>
   */
  public static void logStateEvent(@NotNull EventLogGroup group, @NotNull String event, @NotNull FeatureUsageData data) {
    FeatureUsageLogger.INSTANCE.logState(group, event, data.build());
    FeatureUsageLogger.INSTANCE.logState(group, INVOKED);
  }

  /**
   * <p>
   * Low-level API to record IDE/project state.
   * Using it directly is error-prone because you'll need to think about metric baseline.
   * <b>Don't</b> use it unless absolutely necessary.
   * </p>
   * <br/>
   * <p>
   * Consider using counter events {@link FUCounterUsageLogger} or
   * state events recorded by a scheduler {@link ApplicationUsagesCollector} or {@link ProjectUsagesCollector}
   * </p>
   */
  public static void logStateEvents(@NotNull EventLogGroup group, @NotNull Collection<MetricEvent> events) {
    for (MetricEvent event : events) {
      FeatureUsageLogger.INSTANCE.logState(group, event.getEventId(), event.getData().build());
    }
    FeatureUsageLogger.INSTANCE.logState(group, INVOKED);
  }
}
