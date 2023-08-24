// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.internal.DefaultFinishEvent
import org.gradle.tooling.events.lifecycle.internal.DefaultBuildPhaseFinishEvent
import org.gradle.tooling.events.task.internal.DefaultTaskFailureResult
import org.gradle.tooling.events.task.internal.DefaultTaskFinishEvent
import org.jetbrains.plugins.gradle.service.execution.statistics.AggregatedTaskReport
import org.jetbrains.plugins.gradle.service.execution.statistics.GradleExecutionStageHandler
import org.jetbrains.plugins.gradle.service.execution.statistics.TaskGraphExecutionReport
import org.jetbrains.plugins.gradle.util.GradleTaskClassifier

class TaskExecutionAggregatedRouter(val handler: GradleExecutionStageHandler) {

  private companion object {
    private const val USER_DEFINED_TASK_NAME_REPLACEMENT = "other"
    private const val UNKNOWN = "unknown"

    private const val ORG_JETBRAINS_PACKAGE = "org.jetbrains."
    private const val ORG_GRADLE_PACKAGE = "org.gradle."

    private const val UP_TO_DATE = "UP-TO-DATE"
    private const val FROM_CACHE = "FROM-CACHE"
    private const val LOAD_BUILD_DESCRIPTOR_NAME = "Load build"
    private const val LOAD_PROJECTS_DESCRIPTOR_NAME = "Load projects"
    private const val RUN_BUILD_DESCRIPTOR_NAME = "Run build"
    private const val EVALUATE_SETTINGS_DESCRIPTOR_NAME = "Evaluate settings"
    private const val CONTAINER_CALLBACK_ACTION_DESCRIPTOR_NAME = "Execute container callback action"
    private const val CALCULATE_TASK_GRAPH_DESCRIPTOR_NAME = "Calculate task graph"
    private const val RUN_TASKS = "Run tasks"
  }

  private val inflightTasks: MutableMap<String, AggregatedTaskReport> = HashMap()
  private var inflightContainerCallbackDuration: Long = 0
  private var inflightTaskGraphDuration: Long = 0
  private var taskGraphEventEmitted: Boolean = false
  private var flushed: Boolean = false

  fun route(event: ProgressEvent) {
    if (flushed) {
      return
    }
    if (event is DefaultFinishEvent<*, *>) {
      onFinishEvent(event)
    }
  }

  fun flush() {
    if (flushed) {
      return
    }
    handler.onContainerCallbackExecuted(inflightContainerCallbackDuration)
    handler.onTaskGraphCalculated(inflightTaskGraphDuration)
    inflightTasks.values.forEach(handler::onTaskExecuted)
    inflightTasks.clear()
    flushed = true
  }

  private fun onFinishEvent(event: DefaultFinishEvent<*, *>) {
    val stage: String = event.getStage()
    val duration = event.duration()
    when (stage) {
      LOAD_BUILD_DESCRIPTOR_NAME -> handler.onGradleBuildLoaded(duration)
      RUN_BUILD_DESCRIPTOR_NAME -> {
        handler.onGradleExecutionCompleted(duration)
        flush()
      }
      EVALUATE_SETTINGS_DESCRIPTOR_NAME -> handler.onGradleSettingsEvaluated(duration)
      LOAD_PROJECTS_DESCRIPTOR_NAME -> handler.onGradleProjectLoaded(duration)
      CONTAINER_CALLBACK_ACTION_DESCRIPTOR_NAME -> inflightContainerCallbackDuration += duration
      CALCULATE_TASK_GRAPH_DESCRIPTOR_NAME -> inflightTaskGraphDuration += duration
    }
    if (stage == RUN_TASKS) {
      onTaskGraphFinish(event)
    }
    if (event is DefaultTaskFinishEvent) {
      addTaskFinish(event)
    }
  }

  private fun onTaskGraphFinish(event: DefaultFinishEvent<*, *>) {
    if (taskGraphEventEmitted) {
      return
    }
    var fromCache = 0
    var upToDate = 0
    var executed = 0
    inflightTasks.values.forEach {
      fromCache += it.fromCacheCount
      upToDate += it.upToDateCount
      executed += it.count
    }
    val count = if (event is DefaultBuildPhaseFinishEvent) {
      event.descriptor.buildItemsCount
    }
    else {
      executed
    }
    val report = TaskGraphExecutionReport(
      durationMs = event.duration(),
      count = count,
      fromCacheCount = fromCache,
      upToDateCount = upToDate,
      executed = executed
    )
    handler.onTaskGraphExecuted(report)
    taskGraphEventEmitted = true
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
    .let { GradleTaskClassifier.classifyTaskName(it) }

  private fun DefaultTaskFinishEvent.getTaskSource(): String = descriptor.originPlugin?.displayName ?: UNKNOWN

  private fun ProgressEvent.getStage() = descriptor.name

  private fun DefaultFinishEvent<*, *>.duration(): Long = result.run { endTime - startTime }

  private fun isKnownTaskSource(name: String?): Boolean = name != null && (name.startsWith(ORG_GRADLE_PACKAGE) || name.startsWith(
    ORG_JETBRAINS_PACKAGE))
}
