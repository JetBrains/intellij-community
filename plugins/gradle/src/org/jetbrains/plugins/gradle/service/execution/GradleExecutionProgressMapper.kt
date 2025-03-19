// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.impl.ProgressBuildEventImpl
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.util.NlsSafe
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationProgressEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseFinishEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseProgressEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.test.TestProgressEvent
import org.gradle.tooling.events.work.WorkItemProgressEvent
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.util.*

class GradleExecutionProgressMapper {

  private val states: LinkedList<State> = LinkedList()
  private var phasedEventReceived: Boolean = false

  fun map(taskId: ExternalSystemTaskId, event: ProgressEvent): ExternalSystemTaskNotificationEvent? = when (event) {
    is BuildPhaseStartEvent -> {
      phasedEventReceived = true
      states.addLast(event.asState())
      null
    }
    is BuildPhaseFinishEvent -> {
      states.pollLast()
      null
    }
    is FinishEvent -> {
      states.lastOrNull()?.increment()
      event.toEsEvent(taskId)
    }
    else -> event.toEsEvent(taskId)
  }

  fun mapLegacyEvent(taskId: ExternalSystemTaskId, eventDescription: String): ExternalSystemTaskNotificationEvent? {
    if (phasedEventReceived && taskId.type == ExternalSystemTaskType.EXECUTE_TASK) {
      return null
    }
    if (states.isNotEmpty()) {
      return mapLegacyEventToStateKeepingEsEvent(taskId, eventDescription, states.last)
    }
    return GradleProgressEventConverter.legacyConvertProgressBuildEvent(taskId, taskId, eventDescription)
  }

  private fun ProgressEvent.toEsEvent(taskId: ExternalSystemTaskId): ExternalSystemTaskNotificationEvent? {
    val actionItem = getBuildEventActionItem(this) ?: return null
    val state = states.lastOrNull()
    val description = "${getPhaseName(state)}: '$actionItem'"
    val progress = ProgressBuildEventImpl(taskId, null, eventTime, description, state?.totalItems ?: -1, state?.currentItem ?: -1, "items")
    return ExternalSystemBuildEvent(taskId, progress)
  }

  private fun mapLegacyEventToStateKeepingEsEvent(id: ExternalSystemTaskId,
                                                  eventDescription: String,
                                                  state: State): ExternalSystemBuildEvent? {
    val description: String = getLegacyEventActionItem(eventDescription)?.let { "${getPhaseName(state)}: '$it'" } ?: return null
    val progress = ProgressBuildEventImpl(id, null, System.currentTimeMillis(), description, state.totalItems, state.currentItem, "items")
    return ExternalSystemBuildEvent(id, progress)
  }

  @NlsSafe
  private fun getPhaseName(state: State?): String = when (state?.phase) {
    "CONFIGURE_ROOT_BUILD", "CONFIGURE_BUILD" -> GradleBundle.message("progress.title.configure.build")
    "RUN_MAIN_TASKS", "RUN_WORK" -> GradleBundle.message("progress.title.run.task")
    else -> GradleBundle.message("progress.title.build")
  }

  private fun getLegacyEventActionItem(eventDescription: String): @NlsSafe String? = when {
    eventDescription.startsWith("Build model ")
      .and(eventDescription.contains(" for project ") || eventDescription.contains(" for root project "))
    -> trimLegacyEventActionItem(eventDescription)?.replace("'", "")
    eventDescription.startsWith("Build parameterized model ") && eventDescription.contains(" for project ")
    -> trimLegacyEventActionItem(eventDescription)
    eventDescription.startsWith("Resolve files of ") || eventDescription.startsWith("Resolve dependencies ")
    -> trimLegacyEventActionItem(eventDescription)
    eventDescription.startsWith("Configure project ") -> trimLegacyEventActionItem(eventDescription)
    eventDescription.startsWith("Download from ") -> null
    else -> GradleProgressEventConverter.legacyConvertBuildEventDisplayName(eventDescription)
  }

  private fun getBuildEventActionItem(event: ProgressEvent): @NlsSafe String? = when (event) {
    is TaskProgressEvent, is TestProgressEvent -> event.descriptor.name
    is ProjectConfigurationProgressEvent -> event.descriptor.project.projectPath
    is WorkItemProgressEvent -> event.descriptor.className
    else -> null
  }

  private fun trimLegacyEventActionItem(eventDescription: String): @NlsSafe String? {
    val index = eventDescription.indexOf(':')
    if (index < 0) {
      return null
    }
    return eventDescription.substring(index)
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

  private fun BuildPhaseProgressEvent.asState() = State(
    totalItems = descriptor.buildItemsCount.toLong(),
    phase = descriptor.buildPhase
  )
}