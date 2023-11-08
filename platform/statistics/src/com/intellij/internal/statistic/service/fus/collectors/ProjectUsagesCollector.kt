// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.CancellablePromise

/**
 * Use it to create a collector which records project state.
 *
 * To implement a new collector:
 * 1. Inherit the class, implement [ProjectUsagesCollector.getGroup] and [ProjectUsagesCollector.getMetrics] and register collector in plugin.xml.
 * See *fus-collectors.md* for more details.
 * 2. Implement custom validation rules if necessary. For more information see [com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator].
 * 3. If new group is implemented in a platform or a plugin built with IntelliJ Ultimate, YT issue will be created automatically
 * 4. Otherwise, create a YT issue in FUS project with group data scheme and descriptions to register it on the statistics metadata server
 *
 * To test collector:
 * 1. Open "Statistics Event Log" toolwindow.
 * 2. Add group to events test scheme with "Add Group to Events Test Scheme" action.<br>
 * [com.intellij.internal.statistic.devkit.actions.scheme.AddGroupToTestSchemeAction]
 * 3. Record all state collectors with "Record State Collectors to Event Log" action.<br>
 * [com.intellij.internal.statistic.devkit.actions.RecordStateStatisticsEventLogAction]
 *
 * For more information see *fus-collectors.md*.
 *
 * @see ApplicationUsagesCollector
 * @see CounterUsagesCollector
 */
@ApiStatus.Internal
abstract class ProjectUsagesCollector : FeatureUsagesCollector() {
  /**
   * Override this method to compute metrics.
   *
   * @see requiresReadAccess
   * @see requiresSmartMode
   */
  protected open fun getMetrics(project: Project): Set<MetricEvent> = emptySet()

  /**
   * @return `true` if collector should run under read access.
   */
  protected open fun requiresReadAccess(): Boolean = false

  /**
   * @return `true` if collector should run under read access in smart mode.
   * It is used only if [requiresReadAccess] returned `true`.
   */
  protected open fun requiresSmartMode(): Boolean = false

  /**
   * Implement this method to calculate metrics in non-standard situations.
   *
   * [MetricEvent.eventId] should indicate what we measure, e.g. "configured.vcs", "module.jdk".<br>
   * [MetricEvent.data] should contain the value of the measurement, e.g. {"name":"Git"}, {"version":"1.8", "vendor":"OpenJdk"}
   *
   * @see getMetrics
   */
  open suspend fun collect(project: Project): Set<MetricEvent> {
    val needsRead = requiresReadAccess()
    val needsIndexes = requiresSmartMode()

    return if (needsRead && needsIndexes) {
      smartReadAction(project) {
        getMetrics(project)
      }
    }
    else if (needsRead) {
      readAction {
        if (!project.isDisposed) getMetrics(project) else emptySet()
      }
    }
    else {
      getMetrics(project)
    }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated(message = "Override suspend fun collect(project) instead, this method no longer called")
  open fun getMetrics(project: Project, indicator: ProgressIndicator?): CancellablePromise<out Set<MetricEvent>> {
    return AsyncPromise<Set<MetricEvent>>().apply { setResult(emptySet()) }
  }
}