// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.impl.ProgressBuildEventImpl
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationFinishEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationProgressEvent
import org.gradle.tooling.events.configuration.ProjectConfigurationStartEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseFinishEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseProgressEvent
import org.gradle.tooling.events.lifecycle.BuildPhaseStartEvent
import org.gradle.tooling.events.task.TaskFinishEvent
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.task.TaskStartEvent
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.jetbrains.plugins.gradle.service.execution.GradleProgressListener.GradleProgressState
import org.jetbrains.plugins.gradle.service.execution.GradleProgressListener.ProgressPhase.CONFIGURATION
import org.jetbrains.plugins.gradle.service.execution.GradleProgressListener.ProgressPhase.EXECUTION
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleBundle

internal object GradleProgressIndicatorEventHelper {

  @JvmStatic
  fun areGradleBuildProgressEventsSupported(effectiveSettings: GradleExecutionSettings): Boolean {
    val gradleVersion = extractGradleVersion(effectiveSettings)
    return areGradleBuildProgressEventsSupported(gradleVersion)
  }

  @JvmStatic
  fun extractGradleVersion(settings: GradleExecutionSettings?): GradleVersion? {
    settings ?: return null
    val gradleStringVersion = GradleInstallationManager.getGradleVersion(settings.gradleHome)
    return GradleInstallationManager.getGradleVersionSafe(gradleStringVersion)
  }

  @JvmStatic
  fun areGradleBuildProgressEventsSupported(gradleVersion: GradleVersion?): Boolean {
    gradleVersion ?: return false
    return GradleVersion.version("7.6") <= gradleVersion
  }

  @JvmStatic
  fun isGradleBuildProgressEvent(event: ProgressEvent): Boolean {
    return event is BuildPhaseProgressEvent ||
           event is TaskProgressEvent ||
           event is ProjectConfigurationProgressEvent
  }

  @JvmStatic
  fun createProgressIndicatorEvent(
    taskId: ExternalSystemTaskId,
    id: Any,
    event: ProgressEvent,
    progressState: GradleProgressState
  ): ExternalSystemTaskNotificationEvent? {
    val workItemId = event.progressItemId() ?: return null
    when {
      event.isWorkItemStartEventForCurrentPhase(progressState) -> {
        progressState.addRunningWorkItem(workItemId)
        progressState.incrementCurrentProgress()
      }
      event.isWorkItemFinishEventForCurrentPhase(progressState) -> {
        progressState.removeRunningWorkItem(workItemId)
      }
    }

    val operationName = progressState.operationName() ?: return null
    val total = progressState.totalItems
    val progress = progressState.currentProgress
    return ExternalSystemBuildEvent(
      taskId,
      ProgressBuildEventImpl(id, null, event.eventTime, "$operationName...", total, progress, "items")
    )
  }

  private fun ProgressEvent.isWorkItemStartEventForCurrentPhase(progressState: GradleProgressState) = when (progressState.currentPhase) {
    CONFIGURATION -> this is ProjectConfigurationStartEvent
    EXECUTION -> this is TaskStartEvent
    else -> false
  }

  private fun ProgressEvent.isWorkItemFinishEventForCurrentPhase(progressState: GradleProgressState) = when (progressState.currentPhase) {
    CONFIGURATION -> this is ProjectConfigurationFinishEvent
    EXECUTION -> this is TaskFinishEvent
    else -> false
  }

  private fun ProgressEvent.progressItemId(): String? = when (this) {
    is TaskProgressEvent -> descriptor.taskPath
    is ProjectConfigurationProgressEvent -> descriptor.project.projectPath
    else -> null
  }

  private fun GradleProgressState.operationName(): String? {
    val runningItem = firstRunningWorkItem ?: return null
    return if (currentPhase.isExecution) {
      "${GradleBundle.message("progress.title.run.tasks")}: $runningItem"
    }
    else {
      "${GradleBundle.message("progress.title.configure.projects")}: $runningItem"
    }
  }

  @JvmStatic
  fun maybeUpdateGradleProgressState(myGradleProgressState: GradleProgressState, event: ProgressEvent): GradleProgressState {
    if (event !is BuildPhaseProgressEvent) return myGradleProgressState

    val buildPhase = event.descriptor.buildPhase
    if (event is BuildPhaseStartEvent) {
      return when {
        "CONFIGURE_ROOT_BUILD" == buildPhase -> {
          // When root build starts configuring, build gets from initialization to configuration phase
          myGradleProgressState.setIsConfiguringRootBuild(true)
          val totalItems = event.descriptor.buildItemsCount.toLong()
          GradleProgressState(totalItems, CONFIGURATION)
        }
        "CONFIGURE_BUILD" == buildPhase && myGradleProgressState.currentPhase.isConfiguration -> {
          // Ignore configuration events if in initialization phase: these will be triggered if user has settings plugins
          val additionalItems = event.descriptor.buildItemsCount.toLong()
          myGradleProgressState.incrementTotalItems(additionalItems)
          myGradleProgressState
        }
        "RUN_MAIN_TASKS" == buildPhase && !myGradleProgressState.isConfiguringRootBuild -> {
          // Ignore execution events from nested or buildSrc builds before the root build is done configuring
          val totalItems = event.descriptor.buildItemsCount.toLong()
          GradleProgressState(totalItems, EXECUTION);
        }
        "RUN_WORK" == buildPhase && myGradleProgressState.currentPhase.isExecution -> {
          val additionalItems = event.descriptor.buildItemsCount.toLong()
          myGradleProgressState.incrementTotalItems(additionalItems)
          myGradleProgressState
        }
        else -> myGradleProgressState
      }
    }

    return when {
      event is BuildPhaseFinishEvent && "CONFIGURE_ROOT_BUILD" == buildPhase -> {
        // Configure root build finish event is send after everything else is done configuring
        myGradleProgressState.setIsConfiguringRootBuild(false)
        myGradleProgressState
      }
      else -> myGradleProgressState
    }
  }

}