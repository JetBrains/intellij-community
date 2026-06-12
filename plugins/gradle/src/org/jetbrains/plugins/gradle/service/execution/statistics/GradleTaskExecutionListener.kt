// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.statistics

import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.internal.DefaultTaskFailureResult
import org.jetbrains.plugins.gradle.util.GradleTaskClassifier.classifyTaskName

class GradleTaskExecutionListener(val handler: GradleTaskExecutionHandler) {

  private companion object {
    private const val USER_DEFINED_TASK_NAME_REPLACEMENT = "other"
    private const val UNKNOWN = "unknown"

    private const val ORG_JETBRAINS_PACKAGE = "org.jetbrains."
    private const val ORG_GRADLE_PACKAGE = "org.gradle."

    private const val UP_TO_DATE = "UP-TO-DATE"
    private const val FROM_CACHE = "FROM-CACHE"

    private val QUOTED_IDENTIFIER: Regex = Regex("'([^']+)'")
  }

  private val inflightTasks: MutableMap<String, AggregatedTaskReport> = HashMap()
  private var flushed: Boolean = false

  fun route(event: ProgressEvent) {
    if (flushed) {
      return
    }
    if (event is TaskFinishEvent) {
      addTaskFinish(event)
    }
  }

  fun flush() {
    if (flushed) {
      return
    }
    inflightTasks.values.forEach(handler::onTaskExecuted)
    inflightTasks.clear()
    flushed = true
  }

  private fun addTaskFinish(event: TaskFinishEvent) {
    val source = event.getTaskSource()
    var taskName: String = USER_DEFINED_TASK_NAME_REPLACEMENT
    var taskSource: String = UNKNOWN
    if (isKnownTaskSource(source)) {
      taskName = event.getClassifiedTaskName()
      taskSource = source
    }
    val accumulatorKey = taskName + taskSource
    inflightTasks
      .computeIfAbsent(accumulatorKey) { AggregatedTaskReport(name = taskName, plugin = taskSource) }
      .apply {
        val eventName = event.displayName
        val taskDuration = event.duration()
        if (eventName.contains(UP_TO_DATE)) {
          upToDateCount += 1
          upToDateDuration += taskDuration
        }
        if (eventName.contains(FROM_CACHE)) {
          fromCacheCount += 1
          fromCacheDuration += taskDuration
        }
        if (event.result is DefaultTaskFailureResult) {
          failed += 1
        }
        sumDurationMs += taskDuration
        count += 1
      }
  }

  private fun TaskFinishEvent.getClassifiedTaskName(): String = descriptor.taskPath
    .split(":")
    .last()
    .trim()
    .let { classifyTaskName(it) }

  private fun TaskFinishEvent.getTaskSource(): String {
    val displayName = descriptor.originPlugin?.displayName ?: return UNKNOWN
    return QUOTED_IDENTIFIER.find(displayName)?.groupValues?.get(1) ?: displayName
  }

  private fun TaskFinishEvent.duration(): Long = result.run { endTime - startTime }

  private fun isKnownTaskSource(name: String?): Boolean = name != null && (name.startsWith(ORG_GRADLE_PACKAGE) || name.startsWith(
    ORG_JETBRAINS_PACKAGE))
}
