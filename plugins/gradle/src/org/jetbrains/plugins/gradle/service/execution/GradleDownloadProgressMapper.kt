// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.BuildEvent
import com.intellij.build.events.impl.FileDownloadEventImpl
import com.intellij.build.events.impl.FileDownloadedEventImpl
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.util.NlsSafe
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationResult
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.StatusEvent
import org.gradle.tooling.events.download.FileDownloadProgressEvent
import org.jetbrains.plugins.gradle.util.GradleBundle

class GradleDownloadProgressMapper {

  private companion object {
    private const val BYTES = "bytes"
    private const val DOWNLOAD_SPACE = "Download "
  }

  private val inFlightDownloads: MutableMap<String /*event.descriptor.name - Operation name*/, StatusEvent> = HashMap()

  fun canMap(event: ProgressEvent): Boolean = event is FileDownloadProgressEvent
                                              || (event is StatusEvent && event.unit == BYTES)
                                              || (event is FinishEvent && inFlightDownloads.containsKey(event.descriptor.name))

  fun map(taskId: ExternalSystemTaskId, event: ProgressEvent): ExternalSystemBuildEvent? {
    val operationName: @NlsSafe String = event.descriptor.name
    return when {
      event is StatusEvent && BYTES == event.unit -> getDownloadProgressEvent(taskId, operationName, event)
      event is FinishEvent -> getDownloadFinishEvent(taskId, operationName, event)
      else -> null
    }?.let { ExternalSystemBuildEvent(taskId, it) }
  }

  private fun getDownloadProgressEvent(taskId: ExternalSystemTaskId,
                                       operationName: @NlsSafe String,
                                       newEvent: StatusEvent): BuildEvent? {
    val oldEvent = inFlightDownloads[operationName]
    inFlightDownloads[operationName] = newEvent
    if (oldEvent == null || oldEvent.progress <= newEvent.progress) {
      val path = getPath(operationName) ?: return null
      val progress = if (newEvent.progress > 0) newEvent.progress else 0
      val total = if (newEvent.total > 0) newEvent.total else 0
      val message = GradleBundle.message("progress.title.download", path)
      return FileDownloadEventImpl(
        taskId, null, newEvent.eventTime,
        message, total, progress,
        BYTES, oldEvent == null, path
      )
    }
    return null
  }

  private fun getDownloadFinishEvent(taskId: ExternalSystemTaskId,
                                     operationName: @NlsSafe String,
                                     event: FinishEvent): BuildEvent? {
    inFlightDownloads.remove(operationName) ?: return null
    val path = getPath(operationName) ?: return null
    return FileDownloadedEventImpl(taskId, null, event.eventTime, operationName, event.result.duration(), path)
  }

  private fun OperationResult.duration(): Long {
    val duration = endTime - startTime
    return if (duration > 0) duration else 0L
  }

  private fun getPath(operation: String): @NlsSafe String? {
    return if (operation.startsWith(DOWNLOAD_SPACE)) {
      operation.substring(DOWNLOAD_SPACE.length).trim()
    }
    else {
      null
    }
  }
}