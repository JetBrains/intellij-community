// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.impl.FileDownloadEventImpl
import com.intellij.build.events.impl.FileDownloadedEventImpl
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.externalSystem.service.ExternalSystemTaskProgressIndicatorUpdater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.rawProgressReporter
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.progress.withRawProgressReporter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import kotlin.coroutines.cancellation.CancellationException

class GradleExternalSystemTaskProgressIndicatorUpdater(private val cs: CoroutineScope) : ExternalSystemTaskProgressIndicatorUpdater() {

  private val inFlightDownloadIndicators: MutableMap<ExternalSystemTaskId, MutableMap<String, DownloadProgressIndicator>> = ConcurrentHashMap()

  override fun canUpdate(externalSystemId: ProjectSystemId): Boolean = GradleConstants.SYSTEM_ID == externalSystemId

  override fun getText(description: String,
                       progress: Long,
                       total: Long,
                       unit: String,
                       textWrapper: Function<String, String>): String = textWrapper.apply(description)

  override fun updateIndicator(event: ExternalSystemTaskNotificationEvent,
                               indicator: ProgressIndicator,
                               textWrapper: Function<String, String>) {
    if (event !is ExternalSystemBuildEvent) {
      return
    }
    if (event.buildEvent is FileDownloadEventImpl || event.buildEvent is FileDownloadedEventImpl) {
      onDownloadEvent(event, textWrapper)
      return
    }
    if (event.buildEvent.message.contains("Downloading ")) {
      return
    }
    super.updateIndicator(event, indicator, textWrapper)
  }

  override fun onEnd(taskId: ExternalSystemTaskId) {
    val currentIndicators = inFlightDownloadIndicators.remove(taskId)
    if (currentIndicators == null) {
      return
    }
    currentIndicators.values.forEach { it.stop() }
  }

  private fun onDownloadEvent(event: ExternalSystemBuildEvent, textWrapper: Function<String, String>) {
    val buildEvent = event.buildEvent
    val taskIndicators = inFlightDownloadIndicators.computeIfAbsent(event.id) { ConcurrentHashMap() }
    var indicator = taskIndicators[buildEvent.message]
    if (indicator == null && buildEvent is FileDownloadedEventImpl) {
      return
    }
    if (indicator == null) {
      val project = event.id.findProject()
      if (project == null || project.isDisposed) {
        return
      }
      indicator = DownloadProgressIndicator(buildEvent.message, cs, project, textWrapper, {
        taskIndicators.remove(buildEvent.message)
      })
      indicator.start()
      taskIndicators[buildEvent.message] = indicator
    }
    indicator.handle(event.buildEvent)
  }

  private class DownloadProgressIndicator(@NlsSafe val title: String,
                                          val cs: CoroutineScope,
                                          val project: Project,
                                          val textWrapper: Function<String, String>,
                                          val cleaner: Runnable,
                                          private val channel: Channel<FileDownloadEventImpl> = Channel { }) {

    fun start() {
      cs.launch {
        withBackgroundProgress(project, textWrapper.apply(title), cancellable = false) {
          withRawProgressReporter {
            var previousFraction = 0.0
            var statusEvent = channel.receiveCatching().getOrNull()
            while (statusEvent != null && statusEvent.total - statusEvent.progress > 0) {
              val fraction = statusEvent.progress.toDouble() / statusEvent.total.toDouble()
              if (fraction > previousFraction) {
                val text = "${statusEvent.message} (${StringUtil.formatFileSize(statusEvent.progress)} / ${
                  StringUtil.formatFileSize(statusEvent.total)
                })"
                rawProgressReporter?.text(textWrapper.apply(text))
                rawProgressReporter?.fraction(fraction)
                previousFraction = fraction
              }
              statusEvent = channel.receiveCatching().getOrNull()
            }
          }
        }
      }
    }

    fun handle(event: BuildEvent) {
      cs.launch {
        try {
          if (event is FileDownloadEventImpl) {
            channel.send(event)
          }
          if (event is FileDownloadedEventImpl) {
            channel.close()
            cleaner.run()
          }
        }
        catch (_: CancellationException) {
        }
      }
    }

    fun stop() {
      channel.close()
    }
  }
}