// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @see ApplicationUsagesCollector
 */
public abstract class ProjectUsagesCollector extends FeatureUsagesCollector {
  private static final ExtensionPointName<ProjectUsagesCollector> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.projectUsagesCollector");

  @NotNull
  public static Set<ProjectUsagesCollector> getExtensions(@NotNull UsagesCollectorConsumer invoker) {
    return getExtensions(invoker, EP_NAME);
  }

  @NotNull
  public CancellablePromise<? extends Set<MetricEvent>> getMetrics(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    if (requiresReadAccess()) {
      return ReadAction.nonBlocking(() -> getMetrics(project))
        .expireWith(indicator)
        .expireWith(project)
        .submit(NonUrgentExecutor.getInstance());
    }
    return Promises.resolvedCancellablePromise(getMetrics(project));
  }

  /**
   * If you need to perform long blocking operations with Read lock or on EDT,
   * consider using {@link #getMetrics(Project, ProgressIndicator)} along with ReadAction#nonBlocking if needed,
   * or override {@link #requiresReadAccess()} method to wrap metrics gathering with non-blocking read action automatically.
   */
  @NotNull
  protected Set<MetricEvent> getMetrics(@NotNull Project project) {
    return getUsages(project).stream().
      filter(descriptor -> descriptor.getValue() > 0).
      map(descriptor -> {
      if (descriptor.getValue() == 1) {
        return MetricEventFactoryKt.newMetric(descriptor.getKey(), descriptor.getData());
      }
      return MetricEventFactoryKt.newCounterMetric(descriptor.getKey(), descriptor.getValue(), descriptor.getData());
    }).collect(Collectors.toSet());
  }

  /**
   * @return true if collector should be run under read access. The clients of such collectors
   * have to wrap invocation this{@link #getMetrics(Project)} with non-blocking read-action {@link ReadAction#nonBlocking(Runnable)}
   */
  protected boolean requiresReadAccess() {
    return false;
  }

  /**
   * @deprecated use {@link ProjectUsagesCollector#getMetrics(Project, ProgressIndicator)}
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
  public Set<UsageDescriptor> getUsages(@NotNull Project project) {
    return Collections.emptySet();
  }

  /**
   * @deprecated use {@link ProjectUsagesCollector#getData(Project)}
   */
  @Nullable
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2019.3")
  public FUSUsageContext getContext(@NotNull Project project) {
    return null;
  }

  @Nullable
  public FeatureUsageData getData(@NotNull Project project) {
    return ObjectUtils.doIfNotNull(getContext(project), context -> new FeatureUsageData().addFeatureContext(context));
  }
}