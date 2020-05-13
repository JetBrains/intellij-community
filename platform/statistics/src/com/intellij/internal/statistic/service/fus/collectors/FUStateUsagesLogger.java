// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.EventLogSystemEvents;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * <p>Called by a scheduler once a day and records IDE/project state.</p> <br/>
 *
 * <p><b>Don't</b> use it directly unless absolutely necessary.
 * Implement {@link ApplicationUsagesCollector} or {@link ProjectUsagesCollector} instead.</p>
 *
 * <p>To record IDE events (e.g. invoked action, opened dialog) use {@link FUCounterUsageLogger}</p>
 */
public class FUStateUsagesLogger implements UsagesCollectorConsumer {
  public static final Object LOCK = new Object();

  public static FUStateUsagesLogger create() { return new FUStateUsagesLogger(); }

  public @NotNull CompletableFuture<Void> logProjectStates(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    synchronized (LOCK) {
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      for (ProjectUsagesCollector usagesCollector : ProjectUsagesCollector.getExtensions(this)) {
        final EventLogGroup group = new EventLogGroup(usagesCollector.getGroupId(), usagesCollector.getVersion());
        try {
          futures.add(logUsagesAsStateEvents(project, group, usagesCollector.getData(project),
                                             usagesCollector.getMetrics(project, indicator)));
        }
        catch (Throwable th) {
          futures.add(logCollectingUsageFailed(project, group, th));
        }
      }
      return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
  }

  public @NotNull CompletableFuture<Void> logApplicationStates() {
    synchronized (LOCK) {
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      for (ApplicationUsagesCollector usagesCollector : ApplicationUsagesCollector.getExtensions(this)) {
        final EventLogGroup group = new EventLogGroup(usagesCollector.getGroupId(), usagesCollector.getVersion());
        try {
          futures.add(logUsagesAsStateEvents(null, group, usagesCollector.getData(),
                                             Promises.resolvedPromise(usagesCollector.getMetrics())));
        }
        catch (Throwable th) {
          futures.add(logCollectingUsageFailed(null, group, th));
        }
      }
      return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
  }

  private static @NotNull CompletableFuture<Void> logUsagesAsStateEvents(@Nullable Project project,
                                                                         @NotNull EventLogGroup group,
                                                                         @Nullable FeatureUsageData context,
                                                                         @NotNull Promise<? extends Set<MetricEvent>> metricsPromise) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    metricsPromise.onSuccess(metrics -> {
      if (project != null && project.isDisposed()) {
        return;
      }
      FeatureUsageLogger logger = FeatureUsageLogger.INSTANCE;

      List<CompletableFuture<Void>> futures = new ArrayList<>();
      if (!metrics.isEmpty()) {
        final FeatureUsageData groupData = addProject(project, context);
        for (MetricEvent metric : metrics) {
          final FeatureUsageData data = mergeWithEventData(groupData, metric.getData());
          final Map<String, Object> eventData = data != null ? data.build() : Collections.emptyMap();
          futures.add(logger.logState(group, metric.getEventId(), eventData));
        }
      }
      futures.add(logger.logState(group, EventLogSystemEvents.STATE_COLLECTOR_INVOKED, new FeatureUsageData().addProject(project).build()));
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .whenComplete((result, throwable) -> future.complete(null));
    });
    return future;
  }

  private static @NotNull CompletableFuture<Void> logCollectingUsageFailed(@Nullable Project project,
                                                                           @NotNull EventLogGroup group,
                                                                           @NotNull Throwable error) {
    if (project != null && project.isDisposed()) {
      return CompletableFuture.completedFuture(null);
    }
    return FeatureUsageLogger.INSTANCE.logState(group, EventLogSystemEvents.STATE_COLLECTOR_FAILED,
                                                new FeatureUsageData().addProject(project).build());
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
    FeatureUsageLogger.INSTANCE.logState(group, EventLogSystemEvents.STATE_COLLECTOR_INVOKED);
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
    FeatureUsageLogger.INSTANCE.logState(group, EventLogSystemEvents.STATE_COLLECTOR_INVOKED);
  }
}
