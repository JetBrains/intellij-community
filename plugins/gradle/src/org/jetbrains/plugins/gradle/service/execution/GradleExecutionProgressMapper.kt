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
import org.gradle.tooling.events.lifecycle.BuildPhaseFinishEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseProgressEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.test.TestProgressEvent
import org.jetbrains.plugins.gradle.util.GradleBundle
import java.util.*

class GradleExecutionProgressMapper {

  private var states: LinkedList<State> = LinkedList()

  fun map(taskId: ExternalSystemTaskId, event: ProgressEvent): ExternalSystemTaskNotificationEvent? = when (event) {
    is BuildPhaseStartEvent -> {
      states.addLast(event.asState())
      null
    }
    is BuildPhaseFinishEvent -> {
      states.pollLast()
      null
    }
    is StartEvent -> event.toEsEvent(taskId)
    is FinishEvent -> {
      states.lastOrNull()?.increment()
      event.toEsEvent(taskId)
    }
    else -> GradleProgressEventConverter.convertProgressBuildEvent(taskId, taskId, event)
  }

  fun mapLegacyEvent(taskId: ExternalSystemTaskId, eventDescription: String): ExternalSystemTaskNotificationEvent? {
    if (states.isNotEmpty()) {
      return mapLegacyEventToEsEvent(taskId, eventDescription)
    }
    return GradleProgressEventConverter.legacyConvertProgressBuildEvent(taskId, taskId, eventDescription)
  }

  private fun ProgressEvent.toEsEvent(taskId: ExternalSystemTaskId): ExternalSystemTaskNotificationEvent? {
    var description = convertBuildEventDisplayName(this)
    if (description.isNullOrEmpty()) {
      return null
    }
    val state = states.lastOrNull()
    if (state != null) {
      description = "${getPhaseName(state)}: $description"
    }
    val progress = ProgressBuildEventImpl(taskId, null, eventTime, description, state?.totalItems ?: -1, state?.currentItem ?: -1, "items")
    return ExternalSystemBuildEvent(taskId, progress)
  }

  private fun mapLegacyEventToEsEvent(id: ExternalSystemTaskId, eventDescription: String): ExternalSystemBuildEvent? {
    var description: String = getMeaningfulLegacyDescription(eventDescription) ?: return null
    val state = states.lastOrNull()
    if (state != null) {
      description = "${getPhaseName(state)}: $description"
    }
    val progress = ProgressBuildEventImpl(id, null, System.currentTimeMillis(), description, state?.totalItems ?: -1,
                                          state?.currentItem ?: -1, "items")
    return ExternalSystemBuildEvent(id, progress)
  }

  @NlsSafe
  private fun getPhaseName(state: State): String = when (state.phase) {
    "CONFIGURE_ROOT_BUILD", "CONFIGURE_BUILD" -> GradleBundle.message("progress.title.configure.build")
    "RUN_MAIN_TASKS", "RUN_WORK" -> GradleBundle.message("progress.title.run.task")
    else -> GradleBundle.message("progress.title.build")
  }

  @NlsSafe
  private fun getLastWord(string: String): String? = string.let {
    val words = it.split(" ")
    if (words.size > 1) {
      return words[words.size - 1]
    }
    return null
  }

  @NlsSafe
  private fun getPenultimateWord(string: String): String? = string.let {
    val words = it.split(" ")
    if (words.size >= 2) {
      return words[words.size - 2]
    }
    return null
  }

  @NlsSafe
  private fun getMeaningfulLegacyDescription(eventDescription: String): String? = when {
    eventDescription.startsWith("Build model ") && eventDescription.contains(" for ")
    -> {
      if (eventDescription.contains(" for project ") || eventDescription.contains(" for root project "))
        getLastWord(eventDescription)?.replace("'", "")
      else null
    }
    eventDescription.startsWith("Build parameterized model ") && eventDescription.contains(" for project ")
    -> getLastWord(eventDescription)
    eventDescription.startsWith("Resolve files of ") || eventDescription.startsWith("Resolve dependencies ")
    -> getLastWord(eventDescription)
    eventDescription.startsWith("Configure project ") -> getLastWord(eventDescription)
    else -> GradleProgressEventConverter.legacyConvertBuildEventDisplayName(eventDescription)
  }

  @NlsSafe
  private fun convertBuildEventDisplayName(event: ProgressEvent): @NlsSafe String? = when {
    event is TaskProgressEvent || event is TestProgressEvent -> getPenultimateWord(event.displayName)
    event.displayName.startsWith("Configure project ") || event.displayName.startsWith("Cross-configure project ")
    -> getPenultimateWord(event.displayName)
    else -> null
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