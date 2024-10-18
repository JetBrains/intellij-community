// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.events.BuildEventsNls
import com.intellij.build.events.impl.*
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskState
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.ConcurrencyUtil


fun whenTaskCanceled(task: ExternalSystemTask, callback: () -> Unit) {
  val wrappedCallback = ConcurrencyUtil.once(callback)
  val progressManager = ExternalSystemProgressNotificationManager.getInstance()
  val notificationListener = object : ExternalSystemTaskNotificationListener {
    override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
      wrappedCallback.run()
    }
  }
  progressManager.addNotificationListener(task.id, notificationListener)
  if (task.state == ExternalSystemTaskState.CANCELED || task.state == ExternalSystemTaskState.CANCELING) {
    wrappedCallback.run()
  }
}

fun submitProgressStarted(
  task: ExternalSystemTask,
  taskNotificationListener: ExternalSystemTaskNotificationListener,
  progressIndicator: ProgressIndicator,
  eventId: Any,
  defaultMessage: String
) {
  val message = progressIndicator.text ?: defaultMessage
  val buildEvent = StartEventImpl(eventId, task.id, System.currentTimeMillis(), message)
  val notificationEvent = ExternalSystemBuildEvent(task.id, buildEvent)
  taskNotificationListener.onStatusChange(notificationEvent)
}

fun submitProgressFinished(
  task: ExternalSystemTask,
  taskNotificationListener: ExternalSystemTaskNotificationListener,
  progressIndicator: ProgressIndicator?,
  eventId: Any,
  defaultMessage: String
) {
  val result = when {
    progressIndicator?.isCanceled ?: false -> SkippedResultImpl()
    else -> SuccessResultImpl()
  }
  val message = progressIndicator?.text ?: defaultMessage
  val buildEvent = FinishEventImpl(eventId, task.id, System.currentTimeMillis(), message, result)
  val notificationEvent = ExternalSystemBuildEvent(task.id, buildEvent)
  taskNotificationListener.onStatusChange(notificationEvent)
}

fun submitProgressFailed(
  task: ExternalSystemTask,
  taskNotificationListener: ExternalSystemTaskNotificationListener,
  eventId: Any,
  message: @BuildEventsNls.Message String,
  error: Throwable?
) {
  val result = FailureResultImpl(message, error)
  val buildEvent = FinishEventImpl(eventId, task.id, System.currentTimeMillis(), message, result)
  val notificationEvent = ExternalSystemBuildEvent(task.id, buildEvent)
  taskNotificationListener.onStatusChange(notificationEvent)
}

fun submitProgressStatus(
  task: ExternalSystemTask,
  taskNotificationListener: ExternalSystemTaskNotificationListener,
  progressIndicator: ProgressIndicator,
  eventId: Any,
  defaultMessage: String
) {
  val progress = (progressIndicator.fraction * 100).toLong()
  val message = progressIndicator.text ?: defaultMessage
  val buildEvent = ProgressBuildEventImpl(eventId, task.id, System.currentTimeMillis(), message, 100, progress, "%")
  val notificationEvent = ExternalSystemBuildEvent(task.id, buildEvent)
  taskNotificationListener.onStatusChange(notificationEvent)
}