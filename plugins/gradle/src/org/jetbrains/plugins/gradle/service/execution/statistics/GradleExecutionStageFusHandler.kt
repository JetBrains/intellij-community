// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.statistics

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.statistics.GradleExecutionPerformanceCollector
import java.lang.ref.WeakReference

class GradleExecutionStageFusHandler(val taskId: Long, val project: WeakReference<Project>) : GradleExecutionStageHandler {

  override fun onGradleExecutionCompleted(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.EXECUTION_COMPLETED)

  override fun onGradleBuildLoaded(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.BUILD_LOADED)

  override fun onGradleSettingsEvaluated(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.SETTINGS_EVALUATED)

  override fun onGradleProjectLoaded(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.PROJECT_LOADED)

  override fun onContainerCallbackExecuted(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.CONTAINER_CALLBACK_EXECUTED)

  override fun onTaskGraphCalculated(duration: Long) = duration.emit(GradleExecutionPerformanceCollector.TASK_GRAPH_CALCULATED)

  override fun onTaskGraphExecuted(report: TaskGraphExecutionReport) = report.emit(GradleExecutionPerformanceCollector.TASK_GRAPH_EXECUTED)

  override fun onTaskExecuted(task: AggregatedTaskReport) = task.emit(GradleExecutionPerformanceCollector.TASK_EXECUTED)

  private fun Long.emit(target: EventId2<Long, Long>) = target.log(project.get(), taskId, this)

  private fun TaskGraphExecutionReport.emit(target: VarargEventId) {
    val events = listOf(
      GradleExecutionPerformanceCollector.EXTERNAL_TASK_ID.with(taskId),
      EventFields.DurationMs.with(durationMs),
      EventFields.Count.with(count),
      GradleExecutionPerformanceCollector.UP_TO_DATE_COUNT.with(upToDateCount),
      GradleExecutionPerformanceCollector.FROM_CACHE_COUNT.with(fromCacheCount),
      GradleExecutionPerformanceCollector.EXECUTED.with(executed),
    )
    target.log(project.get(), events)
  }

  private fun AggregatedTaskReport.emit(target: VarargEventId) {
    val events = listOf(
      GradleExecutionPerformanceCollector.EXTERNAL_TASK_ID.with(taskId),
      GradleExecutionPerformanceCollector.SUM_DURATION_MS.with(sumDurationMs),
      GradleExecutionPerformanceCollector.NAME.with(name),
      GradleExecutionPerformanceCollector.GRADLE_PLUGIN.with(plugin),
      GradleExecutionPerformanceCollector.UP_TO_DATE_COUNT.with(upToDateCount),
      GradleExecutionPerformanceCollector.FROM_CACHE_COUNT.with(fromCacheCount),
      EventFields.Count.with(count),
      GradleExecutionPerformanceCollector.UP_TO_DATE_DURATION_MS.with(upToDateDuration),
      GradleExecutionPerformanceCollector.FROM_CACHE_DURATION_MS.with(fromCacheDuration),
      GradleExecutionPerformanceCollector.FAILED_COUNT.with(failed),
    )
    target.log(project.get(), events)
  }
}
