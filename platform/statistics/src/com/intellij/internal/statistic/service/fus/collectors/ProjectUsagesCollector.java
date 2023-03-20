// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator;
import com.intellij.openapi.application.NonBlockingReadAction;
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
 * <br/>
 * To implement a new collector:
 * <ol>
 *   <li>Inherit the class, implement {@link ProjectUsagesCollector#getGroup()} and {@link ProjectUsagesCollector#getMetrics(Project)} and register collector in plugin.xml.
 *   See <i>fus-collectors.md</i> for more details.</li>
 *   <li>Implement custom validation rules if necessary. For more information see {@link IntellijSensitiveDataValidator}.</li>
 *   <li>If new group is implemented in a platform or a plugin built with IntelliJ Ultimate, YT issue will be created automatically</li>
 *   <li>Otherwise, create a YT issue in FUS project with group data scheme and descriptions to register it on the statistics metadata server</li>
 * </ol>
 * <br/>
 * To test collector:
 * <ol>
 *  <li>
 *    Open "Statistics Event Log" toolwindow.
 *  </li>
 *  <li>
 *    Add group to events test scheme with "Add Group to Events Test Scheme" action.<br/>
 *    {@link com.intellij.internal.statistic.devkit.actions.scheme.AddGroupToTestSchemeAction}
 *  </li>
 *  <li>
 *    Record all state collectors with "Record State Collectors to Event Log" action.<br/>
 *    {@link com.intellij.internal.statistic.devkit.actions.RecordStateStatisticsEventLogAction}
 *  </li>
 * </ol>
 * <br/>
 * For more information see <i>fus-collectors.md</i>
 *
 * @see ApplicationUsagesCollector
 * @see CounterUsagesCollector
 */
@ApiStatus.Internal
public abstract class ProjectUsagesCollector extends FeatureUsagesCollector {

  @ApiStatus.Internal
  public static final ExtensionPointName<ProjectUsagesCollector> EP_NAME =
    ExtensionPointName.create("com.intellij.statistics.projectUsagesCollector");

  public static @NotNull Set<ProjectUsagesCollector> getExtensions(@NotNull UsagesCollectorConsumer invoker) {
    return getExtensions(invoker, EP_NAME);
  }

  /**
   * Implement this method to calculate metrics.
   * <br/><br/>
   * {@link MetricEvent#eventId} should indicate what we measure, e.g. "configured.vcs", "module.jdk".<br/>
   * {@link MetricEvent#data} should contain the value of the measurement, e.g. {"name":"Git"}, {"version":"1.8", "vendor":"OpenJdk"}
   */
  public @NotNull CancellablePromise<? extends Set<MetricEvent>> getMetrics(@NotNull Project project, @Nullable ProgressIndicator indicator) {
    if (requiresReadAccess()) {
      NonBlockingReadAction<Set<MetricEvent>> action = ReadAction.nonBlocking(() -> getMetrics(project));
      if (indicator != null) {
        action = action.wrapProgress(indicator);
      }
      if (requiresSmartMode()) {
        action = action.inSmartMode(project);
      }
      return action
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
  protected @NotNull Set<MetricEvent> getMetrics(@NotNull Project project) {
    return Collections.emptySet();
  }

  /**
   * @return <code>true</code> if collector should be run under read access. The clients of such collectors
   * have to wrap invocation this {@link #getMetrics(Project)} with non-blocking read-action {@link ReadAction#nonBlocking(Runnable)}
   */
  protected boolean requiresReadAccess() {
    return false;
  }

  /**
   * @return <code>true</code> if collector should be run under read access in smart mode.
   * It is called only if {@link #requiresReadAccess()} returned <code>true</code>.
   */
  protected boolean requiresSmartMode() {
    return false;
  }
}