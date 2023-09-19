// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.impl.FileDownloadEventImpl
import com.intellij.build.events.impl.FileDownloadedEventImpl
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.util.NlsSafe
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.StatusEvent
import org.gradle.tooling.events.download.FileDownloadProgressEvent

class GradleDownloadProgressMapper {

  private val myDownloadStatusEventIds: MutableMap<String, StatusEvent> = HashMap()

  fun canMap(event: ProgressEvent): Boolean = event is FileDownloadProgressEvent
                                              || (event is StatusEvent && event.unit == "bytes")
                                              || (event is FinishEvent && myDownloadStatusEventIds.containsKey(event.descriptor.name))

  fun map(taskId: ExternalSystemTaskId, event: ProgressEvent): ExternalSystemBuildEvent? {
    val operationName: @NlsSafe String = event.descriptor.name
    if (event is StatusEvent && "bytes" == event.unit) {
      val oldStatusEvent = myDownloadStatusEventIds[operationName]
      myDownloadStatusEventIds[operationName] = event
      if (oldStatusEvent == null || oldStatusEvent.progress != event.progress) {
        val progress = if (event.progress > 0) event.progress else 0
        val total = if (event.total > 0) event.total else 0
        val progressEvent = FileDownloadEventImpl(taskId, null, event.eventTime, operationName, total, progress, "bytes",
                                                  oldStatusEvent == null)
        return ExternalSystemBuildEvent(taskId, progressEvent)
      }
    }
    else if (event is FinishEvent) {
      val statusEvent: StatusEvent? = myDownloadStatusEventIds.remove(operationName)
      if (statusEvent != null) {
        val duration = event.result.run { endTime - startTime }
        val progressEvent = FileDownloadedEventImpl(taskId, null, System.currentTimeMillis(), operationName, duration)
        return ExternalSystemBuildEvent(taskId, progressEvent)
      }
    }
    return null
  }
}