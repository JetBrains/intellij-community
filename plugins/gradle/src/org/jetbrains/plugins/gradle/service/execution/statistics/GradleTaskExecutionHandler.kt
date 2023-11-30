// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.statistics

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.statistics.GradleTaskExecutionCollector
import java.lang.ref.WeakReference

class GradleTaskExecutionHandler(val taskId: Long, val project: WeakReference<Project>) {

  fun onTaskExecuted(task: AggregatedTaskReport) = task.emit(GradleTaskExecutionCollector.TASK_EXECUTED)

  private fun AggregatedTaskReport.emit(target: VarargEventId) {
    val events = listOf(
      GradleTaskExecutionCollector.EXTERNAL_TASK_ID.with(taskId),
      GradleTaskExecutionCollector.SUM_DURATION_MS.with(sumDurationMs),
      GradleTaskExecutionCollector.NAME.with(name),
      GradleTaskExecutionCollector.GRADLE_PLUGIN.with(plugin),
      GradleTaskExecutionCollector.UP_TO_DATE_COUNT.with(upToDateCount),
      GradleTaskExecutionCollector.FROM_CACHE_COUNT.with(fromCacheCount),
      EventFields.Count.with(count),
      GradleTaskExecutionCollector.UP_TO_DATE_DURATION_MS.with(upToDateDuration),
      GradleTaskExecutionCollector.FROM_CACHE_DURATION_MS.with(fromCacheDuration),
      GradleTaskExecutionCollector.FAILED_COUNT.with(failed),
    )
    target.log(project.get(), events)
  }
}
