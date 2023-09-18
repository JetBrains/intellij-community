// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.impl.ProgressBuildEventImpl
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.util.NlsSafe
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationProgressEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseFinishEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseProgressEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.test.TestProgressEvent
import org.gradle.tooling.events.transform.TransformProgressEvent

class GradleExecutionProgressHandler {

  private var states: java.util.ArrayDeque<State> = java.util.ArrayDeque()

  fun canHandle(event: ProgressEvent): Boolean = isAnyBuildProgressEvent(event)

  fun handle(taskId: ExternalSystemTaskId, event: ProgressEvent): ExternalSystemTaskNotificationEvent? {
    return when (event) {
      is BuildPhaseStartEvent -> {
        val state = State(
          totalItems = event.descriptor.buildItemsCount.toLong(),
          phase = event.descriptor.buildPhase
        )
        states.addLast(state)
        null
      }
      is BuildPhaseFinishEvent -> {
        states.removeLast()
        null
      }
      is StartEvent -> {
        states.lastOrNull()?.increment()
        event.toEsEvent(taskId)
      }
      is FinishEvent -> event.toEsEvent(taskId)
      else -> null
    }
  }

  companion object {
    @JvmStatic
    fun isAnyBuildProgressEvent(event: ProgressEvent): Boolean {
      return event is BuildPhaseProgressEvent ||
             event is TaskProgressEvent ||
             event is TransformProgressEvent ||
             event is ProjectConfigurationProgressEvent
    }
  }

  private data class State(
    var totalItems: Long,
    var currentItem: Long = 0,
    val phase: String,
  ) {
    fun increment() {
      currentItem = currentItem.inc()
    }
  }

  private fun ProgressEvent.toEsEvent(taskId: ExternalSystemTaskId): ExternalSystemTaskNotificationEvent? {
    val description = convertBuildEventDisplayName(this)
    if (description == null) {
      return null
    }
    val progress = ProgressBuildEventImpl(taskId, null, eventTime, description, states.lastOrNull()?.totalItems ?: -1,
                                          states.lastOrNull()?.currentItem ?: -1, "items")
    return ExternalSystemBuildEvent(taskId, progress)
  }

  private fun convertBuildEventDisplayName(event: ProgressEvent): @NlsSafe String? {
    val description = when {
      event is TaskProgressEvent -> event.displayName
      event is TestProgressEvent -> event.displayName
      event.displayName.startsWith("Configure project ") -> event.displayName
      event.displayName.startsWith("Cross-configure project ") -> event.displayName
      else -> ""
    }
    if (description.isNotEmpty()) {
      return states.lastOrNull()?.phase + " " + description
    }
    return null
  }
}