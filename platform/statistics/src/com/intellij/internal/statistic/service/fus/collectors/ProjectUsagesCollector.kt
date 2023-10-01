// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.NonUrgentExecutor
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.CancellablePromise
import org.jetbrains.concurrency.resolvedCancellablePromise

/**
 *
 * Use it to create a collector which records project state.
 * <br></br>
 * To implement a new collector:
 *
 *  1. Inherit the class, implement [ProjectUsagesCollector.getGroup] and [ProjectUsagesCollector.getMetrics] and register collector in plugin.xml.
 * See *fus-collectors.md* for more details.
 *  1. Implement custom validation rules if necessary. For more information see [IntellijSensitiveDataValidator].
 *  1. If new group is implemented in a platform or a plugin built with IntelliJ Ultimate, YT issue will be created automatically
 *  1. Otherwise, create a YT issue in FUS project with group data scheme and descriptions to register it on the statistics metadata server
 *
 * <br></br>
 * To test collector:
 *
 *  1.
 * Open "Statistics Event Log" toolwindow.
 *
 *  1.
 * Add group to events test scheme with "Add Group to Events Test Scheme" action.<br></br>
 * [com.intellij.internal.statistic.devkit.actions.scheme.AddGroupToTestSchemeAction]
 *
 *  1.
 * Record all state collectors with "Record State Collectors to Event Log" action.<br></br>
 * [com.intellij.internal.statistic.devkit.actions.RecordStateStatisticsEventLogAction]
 *
 *
 * <br></br>
 * For more information see *fus-collectors.md*
 *
 * @see ApplicationUsagesCollector
 *
 * @see CounterUsagesCollector
 */
@ApiStatus.Internal
abstract class ProjectUsagesCollector : FeatureUsagesCollector() {
  /**
   * Implement this method to calculate metrics.
   * <br></br><br></br>
   * [MetricEvent.eventId] should indicate what we measure, e.g. "configured.vcs", "module.jdk".<br></br>
   * [MetricEvent.data] should contain the value of the measurement, e.g. {"name":"Git"}, {"version":"1.8", "vendor":"OpenJdk"}
   */
  open fun getMetrics(project: Project, indicator: ProgressIndicator?): CancellablePromise<out Set<MetricEvent>> {
    if (requiresReadAccess()) {
      var action = ReadAction.nonBlocking<Set<MetricEvent>> { if (project.isDisposed()) emptySet() else getMetrics(project) }
      if (indicator != null) {
        action = action.wrapProgress(indicator)
      }
      if (requiresSmartMode()) {
        action = action.inSmartMode(project)
      }
      return action
        .expireWith(project)
        .submit(NonUrgentExecutor.getInstance())
    }
    return resolvedCancellablePromise(getMetrics(project))
  }

  /**
   * If you need to perform long blocking operations with Read lock or on EDT,
   * consider using [.getMetrics] along with ReadAction#nonBlocking if needed,
   * or override [.requiresReadAccess] method to wrap metrics gathering with non-blocking read action automatically.
   */
  protected open fun getMetrics(project: Project): Set<MetricEvent> {
    return emptySet()
  }

  /**
   * @return `true` if collector should be run under read access. The clients of such collectors
   * have to wrap invocation this [.getMetrics] with non-blocking read-action [ReadAction.nonBlocking]
   */
  protected open fun requiresReadAccess(): Boolean {
    return false
  }

  /**
   * @return `true` if collector should be run under read access in smart mode.
   * It is called only if [.requiresReadAccess] returned `true`.
   */
  protected open fun requiresSmartMode(): Boolean {
    return false
  }
}