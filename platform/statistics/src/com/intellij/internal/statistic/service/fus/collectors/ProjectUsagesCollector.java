// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.eventLog.validator.SensitiveDataValidator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.NonUrgentExecutor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;

import java.util.Collections;
import java.util.Set;

/**
 * <p>Use it to create a collector which records project state.</p>
 *
 * To implement a new collector:
 * <ol>
 *   <li>Inherit the class, implement {@link ProjectUsagesCollector#getMetrics(Project)} and register collector in plugin.xml;</li>
 *   <li>Specify collectors data scheme and implement custom validation rules if necessary.<br/>
 *   For more information see {@link SensitiveDataValidator};</li>
 *   <li>Create an <a href="https://youtrack.jetbrains.com/issues/FUS">issue</a> to add group, its data scheme and description to the whitelist;</li>
 * </ol>
 *
 * To test collector:
 * <ol>
 *  <li>
 *    If group is not whitelisted, add it to local whitelist with "Add Test Group to Local Whitelist" action.<br/>
 *    {@link com.intellij.internal.statistic.actions.scheme.AddGroupToTestSchemeAction}
 *  </li>
 *  <li>
 *    Open toolwindow with event logs with "Show Statistics Event Log" action.<br/>
 *    {@link com.intellij.internal.statistic.actions.ShowStatisticsEventLogAction}
 *  </li>
 *  <li>
 *    Record all state collectors with "Record State Collectors to Event Log" action.<br/>
 *    {@link com.intellij.internal.statistic.actions.RecordStateStatisticsEventLogAction}
 *  </li>
 * </ol>
 * @see ApplicationUsagesCollector
 * @see FUCounterUsageLogger
 */
@ApiStatus.Internal
public abstract class ProjectUsagesCollector extends FeatureUsagesCollector {

  @ApiStatus.Internal
  public static final ExtensionPointName<ProjectUsagesCollector> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.projectUsagesCollector");

  @NotNull
  public static Set<ProjectUsagesCollector> getExtensions(@NotNull UsagesCollectorConsumer invoker) {
    return getExtensions(invoker, EP_NAME);
  }

  /**
   * Implement this method to calculate metrics.
   * <br/><br/>
   * {@link MetricEvent#eventId} should indicate what we measure, e.g. "configured.vcs", "module.jdk".<br/>
   * {@link MetricEvent#data} should contain the value of the measurement, e.g. {"name":"Git"}, {"version":"1.8", "vendor":"OpenJdk"}
   */
  @NotNull
  public CancellablePromise<? extends Set<MetricEvent>> getMetrics(@NotNull Project project, @NotNull ProgressIndicator indicator) {
    if (requiresReadAccess()) {
      return ReadAction.nonBlocking(() -> getMetrics(project))
        .wrapProgress(indicator)
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
    return Collections.emptySet();
  }

  /**
   * @return true if collector should be run under read access. The clients of such collectors
   * have to wrap invocation this{@link #getMetrics(Project)} with non-blocking read-action {@link ReadAction#nonBlocking(Runnable)}
   */
  protected boolean requiresReadAccess() {
    return false;
  }

  @Nullable
  public FeatureUsageData getData(@NotNull Project project) {
    return null;
  }
}