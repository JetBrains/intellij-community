// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.fus

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.statistics.GradleExecutionPerformanceCollector
import java.lang.ref.WeakReference

class GradleExecutionStageFusHandler(val taskId: Long, val project: WeakReference<Project>) : GradleExecutionStageHandler {

  override fun onGradleExecutionCompleted(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.OPERATION_DURATION)

  override fun onGradleBuildLoaded(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.BUILD_LOAD)

  override fun onGradleSettingsEvaluated(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.SETTINGS_EVALUATION)

  override fun onGradleProjectLoaded(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.PROJECT_LOADING)

  override fun onContainerCallbackExecuted(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.CONTAINER_CALLBACK)

  override fun onTaskGraphCalculated(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.TASK_GRAPH_CALCULATION)

  override fun onTasksExecuted(report: TaskGraphExecutionReport) = report.emit(GradleExecutionPerformanceCollector.TASK_GRAPH_EXECUTION)

  override fun onTaskFinished(task: AggregatedTaskReport) = task.emit(GradleExecutionPerformanceCollector.TASK_EXECUTION_FINISHED)

  private fun Long.emit(target: EventId2<Long, Long>) = target.log(project.get(), taskId, this)

  private fun TaskGraphExecutionReport.emit(target: VarargEventId) {
    val events = listOf(
      EventPair(GradleExecutionPerformanceCollector.EXTERNAL_TASK_ID, taskId),
      EventPair(EventFields.DurationMs, durationMs),
      EventPair(EventFields.Count, count),
      EventPair(GradleExecutionPerformanceCollector.UP_TO_DATE_COUNT, upToDateCount),
      EventPair(GradleExecutionPerformanceCollector.FROM_CACHE_COUNT, fromCacheCount),
      EventPair(GradleExecutionPerformanceCollector.EXECUTED, executed),
    )
    target.log(project.get(), events)
  }

  private fun AggregatedTaskReport.emit(target: VarargEventId) {
    val events = listOf(
      EventPair(GradleExecutionPerformanceCollector.EXTERNAL_TASK_ID, taskId),
      EventPair(GradleExecutionPerformanceCollector.SUM_DURATION_MS, sumDurationMs),
      EventPair(GradleExecutionPerformanceCollector.NAME, name),
      EventPair(GradleExecutionPerformanceCollector.GRADLE_PLUGIN, plugin),
      EventPair(GradleExecutionPerformanceCollector.UP_TO_DATE_COUNT, upToDateCount),
      EventPair(GradleExecutionPerformanceCollector.FROM_CACHE_COUNT, fromCacheCount),
      EventPair(EventFields.Count, count),
      EventPair(GradleExecutionPerformanceCollector.UP_TO_DATE_DURATION_MS, upToDateDuration),
      EventPair(GradleExecutionPerformanceCollector.FROM_CACHE_DURATION_MS, fromCacheDuration),
      EventPair(GradleExecutionPerformanceCollector.FAILED_COUNT, failed),
    )
    target.log(project.get(), events)
  }
}
