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
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.util.*

class GradleExecutionProgressMapper {

  private var states: ArrayDeque<State> = ArrayDeque()

  fun canMap(event: ProgressEvent): Boolean = isAnyBuildProgressEvent(event)

  fun map(taskId: ExternalSystemTaskId, event: ProgressEvent): ExternalSystemTaskNotificationEvent? {
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
        if (states.isNotEmpty()) {
          states.removeLast()
        }
        null
      }
      is StartEvent -> event.toEsEvent(taskId)
      is FinishEvent -> {
        states.lastOrNull()?.increment()
        event.toEsEvent(taskId)
      }
      else -> null
    }
  }

  fun mapLegacyEvent(taskId: ExternalSystemTaskId, eventDescription: String): ExternalSystemTaskNotificationEvent? {
    if (states.isNotEmpty()) {
      return mapLegacyEventToEsEvent(taskId, eventDescription)
    }
    return GradleProgressEventConverter.legacyConvertProgressBuildEvent(taskId, taskId, eventDescription)
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

  @NlsSafe
  private fun convertBuildEventDisplayName(event: ProgressEvent): @NlsSafe String? {
    val description = when {
      event is TaskProgressEvent || event is TestProgressEvent -> getTaskName(event.displayName)
      event.displayName.startsWith("Configure project ") || event.displayName.startsWith("Cross-configure project ")
      -> getModuleName(event.displayName)
      else -> ""
    }
    if (description.isNotEmpty()) {
      return "${getPhaseName()}: $description"
    }
    return null
  }

  private fun mapLegacyEventToEsEvent(id: ExternalSystemTaskId, eventDescription: String): ExternalSystemBuildEvent? {
    var description: String? = when {
      eventDescription.startsWith("Build model ") && eventDescription.contains(" for project ")
      -> eventDescription.split(" ").last()
      eventDescription.startsWith("Build model ") && eventDescription.contains(" for root project ")
      -> eventDescription.split(" ").last()
      eventDescription.startsWith("Build parameterized model ") && eventDescription.contains(" for project ")
      -> eventDescription.split(" ").last()
      eventDescription.startsWith("Configure project ") -> eventDescription.split(" ").last()
      else -> GradleProgressEventConverter.legacyConvertBuildEventDisplayName(eventDescription)
    }
    if (description == null) {
      return null
    }
    if (states.isNotEmpty()) {
      description = "${getPhaseName()}: $description"
    }
    val progress = ProgressBuildEventImpl(id, null, System.currentTimeMillis(), description,
                                          states.lastOrNull()?.totalItems ?: -1,
                                          states.lastOrNull()?.currentItem ?: -1, "items")
    return ExternalSystemBuildEvent(id, progress)
  }

  @NlsSafe
  private fun getPhaseName(): String = when (states.lastOrNull()?.phase) {
    "CONFIGURE_ROOT_BUILD", "CONFIGURE_BUILD" -> GradleBundle.message("progress.title.configure.build")
    "RUN_MAIN_TASKS", "RUN_WORK" -> GradleBundle.message("progress.title.run.task")
    else -> GradleBundle.message("progress.title.build")
  }

  @NlsSafe
  private fun getModuleName(moduleEventDescription: String): String {
    val particles = moduleEventDescription.split(" ")
    return getPenultimateElement(particles)
  }

  @NlsSafe
  private fun getTaskName(taskEventDescription: String): String {
    val particles = taskEventDescription.split(" ")
    return getPenultimateElement(particles)
  }

  @NlsSafe
  private fun <T> getPenultimateElement(items: List<T>): T {
    val size = items.size
    if (size > 2) {
      return items[size - 2]
    }
    return items[size - 1]
  }
}