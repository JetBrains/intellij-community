// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import kotlinx.coroutines.CoroutineScope

/**
 * Listens to Gradle download progress events and shows a download progress as progress indicator for every artifact.
 */
interface GradleDownloadProgressListener {
  companion object {
    @JvmStatic
    fun newListener(
      taskId: ExternalSystemTaskId,
      mainListener: ExternalSystemTaskNotificationListener
    ): GradleDownloadProgressListener {
      return when (val project = taskId.findProject()) {
        null -> {
          NoOpGradleDownloadProgressListener()
        }
        else -> {
          val scopeProvider = project.service<GradleProgressCoroutineScopeProvider>()
          GradleDownloadProgressListenerImpl(taskId, project, scopeProvider.cs, mainListener)
        }
      }
    }
  }

  fun updateProgressIndicator(event: ExternalSystemTaskNotificationEvent, runInBackground: Boolean)
  fun stopProgressIndicator(event: ExternalSystemTaskNotificationEvent)
}

private class NoOpGradleDownloadProgressListener : GradleDownloadProgressListener {
  override fun updateProgressIndicator(event: ExternalSystemTaskNotificationEvent, runInBackground: Boolean) {
  }

  override fun stopProgressIndicator(event: ExternalSystemTaskNotificationEvent) {
  }
}