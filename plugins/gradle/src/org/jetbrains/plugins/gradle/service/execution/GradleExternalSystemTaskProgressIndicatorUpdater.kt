// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.ProgressBuildEvent
import com.intellij.build.events.impl.FileDownloadEventImpl
import com.intellij.build.events.impl.FileDownloadedEventImpl
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.externalSystem.service.ExternalSystemTaskProgressIndicatorUpdater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil.formatFileSize
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.GradleCoroutineScope.gradleCoroutineScope
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Function
import kotlin.coroutines.cancellation.CancellationException

private typealias UpdaterState<T> = ConcurrentMap<ExternalSystemTaskId, ConcurrentMap<String /*file uri*/, T>>

class GradleExternalSystemTaskProgressIndicatorUpdater : ExternalSystemTaskProgressIndicatorUpdater() {

  private companion object {
    private const val INDICATOR_THRESHOLD_MILLIS = 400
  }

  private val taskIndicators: UpdaterState<DownloadProgressIndicator> = ConcurrentHashMap()
  private val taskCandidates: UpdaterState<FileDownloadEventImpl> = ConcurrentHashMap()

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
    if (event !is ExternalSystemBuildEvent) {
      return
    }
    if (event.buildEvent is ProgressBuildEvent && (event.buildEvent as ProgressBuildEvent).isInvalid()) {
      return
    }
    when (event.buildEvent) {
      is FileDownloadEventImpl -> onDownloadEvent(event.id, event.buildEvent as FileDownloadEventImpl, textWrapper)
      is FileDownloadedEventImpl -> onDownloadEndEvent(event.id, event.buildEvent as FileDownloadedEventImpl)
      else -> super.updateIndicator(event, indicator, textWrapper)
    }
  }

  override fun onTaskEnd(taskId: ExternalSystemTaskId) {
    taskIndicators.remove(taskId)?.values?.forEach { it.stop() }
    taskCandidates.remove(taskId)
  }

  private fun onDownloadEvent(taskId: ExternalSystemTaskId,
                              event: FileDownloadEventImpl,
                              textWrapper: Function<String, @NlsContexts.ProgressText String>) {
    if (event.isFirstInGroup) {
      val candidates = taskCandidates.computeIfAbsent(taskId) { ConcurrentHashMap() }
      candidates[event.downloadPath] = event
      return
    }
    val mayBeIndicator = taskIndicators[taskId]?.get(event.downloadPath)
    if (mayBeIndicator != null) {
      mayBeIndicator.handle(event)
    }
    else {
      mayBePromoteCandidate(taskId, event, textWrapper)
    }
  }

  private fun mayBePromoteCandidate(taskId: ExternalSystemTaskId,
                                    event: FileDownloadEventImpl,
                                    textWrapper: Function<String, @NlsContexts.ProgressText String>) {
    val candidates = taskCandidates[taskId] ?: return
    val mayBeCandidate = candidates[event.downloadPath] ?: return
    if (!mayBeCandidate.shouldBeVisible(event)) {
      return
    }
    candidates.remove(event.downloadPath)
    val project = taskId.findProject()
    if (project == null || project.isDisposed) {
      return
    }
    val indicators = taskIndicators.computeIfAbsent(taskId) { ConcurrentHashMap() }
    val indicator = DownloadProgressIndicator(event.message, project.gradleCoroutineScope, project, textWrapper)
      .also { it.start() }
    indicators[event.downloadPath] = indicator
  }

  private fun onDownloadEndEvent(taskId: ExternalSystemTaskId, event: FileDownloadedEventImpl) {
    taskIndicators[taskId]?.remove(event.downloadPath)?.handle(event)
    taskCandidates[taskId]?.remove(event.downloadPath)
  }

  private fun ProgressBuildEvent.isInvalid(): Boolean {
    return total < 0 || progress < 0 || unit.isNullOrEmpty()
  }

  private fun FileDownloadEventImpl.shouldBeVisible(currentEvent: FileDownloadEventImpl): Boolean {
    if (System.currentTimeMillis() - eventTime <= INDICATOR_THRESHOLD_MILLIS) {
      return false
    }
    val delta = currentEvent.progress - progress
    return currentEvent.progress + delta < total
  }

  private class DownloadProgressIndicator(@NlsSafe private val title: String,
                                          private val cs: CoroutineScope,
                                          private val project: Project,
                                          private val textWrapper: Function<String, @NlsContexts.ProgressText String>) {

    private val channel: Channel<FileDownloadEventImpl> = Channel()

    fun start() {
      cs.launch {
        withBackgroundProgress(project, textWrapper.apply(title), cancellable = false) {
          reportRawProgress { reporter ->
            var previousFraction = 0.0
            var statusEvent = channel.receiveCatching().getOrNull()
            while (statusEvent != null && statusEvent.total - statusEvent.progress > 0) {
              val fraction = statusEvent.progress.toDouble() / statusEvent.total.toDouble()
              if (fraction > previousFraction) {
                val text = "${statusEvent.progress.toFileSize()} / ${statusEvent.total.toFileSize()}"
                reporter.text(text)
                reporter.fraction(fraction)
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
          }
        }
        catch (_: CancellationException) {
        }
      }
    }

    fun stop() {
      channel.close()
    }

    private fun Long.toFileSize(): @NlsSafe String {
      return formatFileSize(this, " ", -1, true)
    }
  }
}