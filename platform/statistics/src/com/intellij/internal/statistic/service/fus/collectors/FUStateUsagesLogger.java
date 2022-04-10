// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.EventLogSystemEvents;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.fus.FeatureUsageLogger;
import com.intellij.internal.statistic.utils.PluginInfoDetectorKt;
import com.intellij.openapi.diagnostic.Logger;
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
 * <p>To record IDE events (e.g. invoked action, opened dialog) use {@link CounterUsagesCollector}</p>
 */
public final class FUStateUsagesLogger implements UsagesCollectorConsumer {
  private static final Logger LOG = Logger.getInstance(FUStateUsagesLogger.class);
  private static final Object LOCK = new Object();

  public static FUStateUsagesLogger create() { return new FUStateUsagesLogger(); }

  public @NotNull CompletableFuture<Void> logProjectStates(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    synchronized (LOCK) {
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      for (ProjectUsagesCollector usagesCollector : ProjectUsagesCollector.getExtensions(this)) {
        String groupId = usagesCollector.getGroupId();
        if (!PluginInfoDetectorKt.getPluginInfo(usagesCollector.getClass()).isDevelopedByJetBrains()) {
          LOG.warn("Skip '" + groupId + "' because its registered in a third-party plugin");
          continue;
        }

        EventLogGroup group = new EventLogGroup(groupId, usagesCollector.getVersion());
        Promise<? extends Set<MetricEvent>> metrics = usagesCollector.getMetrics(project, indicator);
        futures.add(logMetricsOrError(project, group, metrics));
      }
      return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
  }

  public @NotNull CompletableFuture<Void> logApplicationStates() {
    return logApplicationStates(false);
  }

  public @NotNull CompletableFuture<Void> logApplicationStatesOnStartup() {
    return logApplicationStates(true);
  }

  private @NotNull CompletableFuture<Void> logApplicationStates(boolean onStartup) {
    synchronized (LOCK) {
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      for (ApplicationUsagesCollector usagesCollector : ApplicationUsagesCollector.getExtensions(this)) {
        if (onStartup && !(usagesCollector instanceof AllowedDuringStartupCollector)) {
          continue;
        }
        String groupId = usagesCollector.getGroupId();
        if (!PluginInfoDetectorKt.getPluginInfo(usagesCollector.getClass()).isDevelopedByJetBrains()) {
          LOG.warn("Skip '" + groupId + "' because its registered in a third-party plugin");
          continue;
        }

        EventLogGroup group = new EventLogGroup(groupId, usagesCollector.getVersion());
        Promise<Set<MetricEvent>> metrics = Promises.resolvedPromise(usagesCollector.getMetrics());
        futures.add(logMetricsOrError(null, group, metrics));
      }
      return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
  }

  private static CompletableFuture<Void> logMetricsOrError(@Nullable Project project,
                                                           @NotNull EventLogGroup group,
                                                           @NotNull Promise<? extends Set<MetricEvent>> metricsPromise) {
    try {
      return logUsagesAsStateEvents(project, group, metricsPromise);
    }
    catch (Throwable th) {
      if (project != null && project.isDisposed()) {
        return CompletableFuture.completedFuture(null);
      }

      FeatureUsageData data = new FeatureUsageData().addProject(project);
      return FeatureUsageLogger.INSTANCE.logState(group, EventLogSystemEvents.STATE_COLLECTOR_FAILED, data.build());
    }
  }

  private static @NotNull CompletableFuture<Void> logUsagesAsStateEvents(@Nullable Project project,
                                                                         @NotNull EventLogGroup group,
                                                                         @NotNull Promise<? extends Set<MetricEvent>> metricsPromise) {
    CompletableFuture<Void> future = new CompletableFuture<>();
    metricsPromise.onSuccess(metrics -> {
      if (project != null && project.isDisposed()) {
        return;
      }
      FeatureUsageLogger logger = FeatureUsageLogger.INSTANCE;

      List<CompletableFuture<Void>> futures = new ArrayList<>();
      if (!metrics.isEmpty()) {
        final FeatureUsageData groupData = addProject(project);
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

  @Nullable
  private static FeatureUsageData addProject(@Nullable Project project) {
    if (project == null) {
      return null;
    }
    return new FeatureUsageData().addProject(project);
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
   * Consider using counter events {@link CounterUsagesCollector} or
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
   * Consider using counter events {@link CounterUsagesCollector} or
   * state events recorded by a scheduler {@link ApplicationUsagesCollector} or {@link ProjectUsagesCollector}
   * </p>
   */
  public static @NotNull CompletableFuture<Void> logStateEventsAsync(@NotNull EventLogGroup group, @NotNull Collection<MetricEvent> events) {
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (MetricEvent event : events) {
      futures.add(FeatureUsageLogger.INSTANCE.logState(group, event.getEventId(), event.getData().build()));
    }
    futures.add(FeatureUsageLogger.INSTANCE.logState(group, EventLogSystemEvents.STATE_COLLECTOR_INVOKED));
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
  }

  public static void logStateEvents(@NotNull EventLogGroup group, @NotNull Collection<MetricEvent> events) {
    logStateEventsAsync(group, events);
  }
}
