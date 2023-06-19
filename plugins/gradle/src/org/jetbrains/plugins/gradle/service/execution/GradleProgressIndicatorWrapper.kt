// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.ProgressBuildEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemTaskProgressIndicatorUpdater
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gradle.service.execution.GradleProgressIndicatorEventHelper.areGradleBuildProgressEventsSupported
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.gradle.util.GradleConstants
import kotlin.math.max

internal object GradleProgressIndicatorWrapper {
  fun registerGradleProgressIndicator(
    taskId: ExternalSystemTaskId,
    effectiveSettings: GradleExecutionSettings
  ) {
    if (!areGradleBuildProgressEventsSupported(effectiveSettings)) return
    taskId.findProject()?.let { project ->
      val csProvider = project.service<GradleProgressCoroutineScopeProvider>()
      val progressIndicator = GradleProgressIndicator(project, csProvider.cs)
      ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(taskId, progressIndicator)
    }
  }

  private class GradleProgressIndicator(
    private val project: Project,
    private val cs: CoroutineScope
  ) : ExternalSystemTaskNotificationListenerAdapter() {

    private val channel: Channel<ProgressBuildEvent> = Channel()

    init {
      startProgressIndicator("Gradle build")
    }

    private fun startProgressIndicator(@Suppress("SameParameterValue") title: String) {
      cs.launch {
        withBackgroundProgress(project, title, cancellable = false) {
          withRawProgressReporter {
            rawProgressReporter?.text(wrapText("Initialization..."))
            var nextEvent = channel.receiveCatching().getOrNull()
            while (nextEvent != null) {
              val fraction = when {
                nextEvent.unit == "items" && nextEvent.total > 0 -> max(nextEvent.progress.toDouble() / nextEvent.total, 1.0)
                else -> null
              }
              rawProgressReporter?.fraction(fraction)
              rawProgressReporter?.text(nextEvent.getText())
              nextEvent = channel.receiveCatching().getOrNull()
            }
          }
        }
      }
    }

    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
      if (event is ExternalSystemBuildEvent && event.buildEvent is ProgressBuildEvent) {
        cs.launch {
          try {
            channel.send(event.buildEvent as ProgressBuildEvent)
          }
          catch (_: CancellationException) {
          }
        }
      }
    }

    override fun onEnd(id: ExternalSystemTaskId) {
      channel.close()
    }

    private fun ProgressBuildEvent.getText(): String =
      ExternalSystemTaskProgressIndicatorUpdater.getText(this.message, this.progress, this.total, this.unit) { wrapText(it) }

    private fun wrapText(text: String): @Nls String =
      ExternalSystemBundle.message("progress.update.text", GradleConstants.SYSTEM_ID.readableName, text)
  }
}