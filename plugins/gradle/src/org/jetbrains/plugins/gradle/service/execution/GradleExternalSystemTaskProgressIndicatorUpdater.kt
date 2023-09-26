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
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.service.coroutine.GradleCoroutineScopeProvider
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function
import kotlin.coroutines.cancellation.CancellationException

class GradleExternalSystemTaskProgressIndicatorUpdater : ExternalSystemTaskProgressIndicatorUpdater() {

  private val inFlightDownloadIndicators: MutableMap<ExternalSystemTaskId, MutableMap<String /*URI*/, DownloadProgressIndicator>> = ConcurrentHashMap()

  override fun canUpdate(externalSystemId: ProjectSystemId): Boolean = GradleConstants.SYSTEM_ID == externalSystemId

  @NlsSafe
  override fun getText(description: String,
                       progress: Long,
                       total: Long,
                       unit: String,
                       textWrapper: Function<String, @NlsContexts.ProgressText String>): String = textWrapper.apply(description)

  override fun updateIndicator(event: ExternalSystemTaskNotificationEvent,
                               indicator: ProgressIndicator,
                               textWrapper: Function<String, @NlsContexts.ProgressText String>) {
    if (event !is ExternalSystemBuildEvent || event.buildEvent.message.contains("Downloading ")) {
      return
    }
    if (event.buildEvent is FileDownloadEventImpl || event.buildEvent is FileDownloadedEventImpl) {
      onDownloadEvent(event, textWrapper)
      return
    }
    super.updateIndicator(event, indicator, textWrapper)
  }

  override fun onTaskEnd(taskId: ExternalSystemTaskId): Unit = inFlightDownloadIndicators.remove(taskId)?.values?.forEach { it.stop() } ?: Unit

  private fun onDownloadEvent(event: ExternalSystemBuildEvent, textWrapper: Function<String, @NlsContexts.ProgressText String>) {
    val buildEvent = event.buildEvent
    val taskIndicators = inFlightDownloadIndicators.computeIfAbsent(event.id) { ConcurrentHashMap() }
    val indicator: DownloadProgressIndicator? = taskIndicators.compute(buildEvent.message) { _, oldIndicator ->
      if (oldIndicator == null && buildEvent is FileDownloadedEventImpl) {
        return@compute null
      }
      if (oldIndicator != null) {
        return@compute oldIndicator
      }
      val project = event.id.findProject()
      if (project == null || project.isDisposed) {
        return@compute null
      }
      val csp = GradleCoroutineScopeProvider.getInstance(project)
      DownloadProgressIndicator(buildEvent.message, csp.cs, project, textWrapper) { taskIndicators.remove(buildEvent.message) }
        .also { it.start() }
    }
    indicator?.handle(buildEvent)
  }

  private class DownloadProgressIndicator(@NlsSafe private val title: String,
                                          private val cs: CoroutineScope,
                                          private val project: Project,
                                          private val textWrapper: Function<String, @NlsContexts.ProgressText String>,
                                          private val cleaner: Runnable) {

    private val channel: Channel<FileDownloadEventImpl> = Channel()

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
            channel.trySend(event)
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