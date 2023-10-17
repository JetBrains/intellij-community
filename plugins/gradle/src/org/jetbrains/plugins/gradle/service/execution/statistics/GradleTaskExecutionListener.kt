// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.statistics

import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.internal.DefaultFinishEvent
import org.gradle.tooling.events.task.internal.DefaultTaskFailureResult
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent
import org.jetbrains.plugins.gradle.util.GradleTaskClassifier.classifyTaskName
import kotlin.apply
import kotlin.collections.forEach
import kotlin.collections.last
import kotlin.let
import kotlin.run
import kotlin.text.contains
import kotlin.text.split
import kotlin.text.startsWith
import kotlin.text.trim

class GradleTaskExecutionListener(val handler: GradleTaskExecutionHandler) {

  private companion object {
    private const val USER_DEFINED_TASK_NAME_REPLACEMENT = "other"
    private const val UNKNOWN = "unknown"

    private const val ORG_JETBRAINS_PACKAGE = "org.jetbrains."
    private const val ORG_GRADLE_PACKAGE = "org.gradle."

    private const val UP_TO_DATE = "UP-TO-DATE"
    private const val FROM_CACHE = "FROM-CACHE"
  }

  private val inflightTasks: MutableMap<String, AggregatedTaskReport> = HashMap()
  private var flushed: Boolean = false

  fun route(event: ProgressEvent) {
    if (flushed) {
      return
    }
    if (event is DefaultTaskFinishEvent) {
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

  private fun addTaskFinish(event: DefaultTaskFinishEvent) {
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

  private fun DefaultTaskFinishEvent.getClassifiedTaskName(): String = descriptor.taskPath
    .split(":")
    .last()
    .trim()
    .let { classifyTaskName(it) }

  private fun DefaultTaskFinishEvent.getTaskSource(): String = descriptor.originPlugin?.displayName ?: UNKNOWN

  private fun DefaultFinishEvent<*, *>.duration(): Long = result.run { endTime - startTime }

  private fun isKnownTaskSource(name: String?): Boolean = name != null && (name.startsWith(ORG_GRADLE_PACKAGE) || name.startsWith(
    ORG_JETBRAINS_PACKAGE))
}
