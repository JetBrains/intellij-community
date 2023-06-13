// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import kotlinx.coroutines.CoroutineScope

/**
 * Listens to Gradle download progress events and shows a download progress as progress indicator for every artifact.
 */
interface GradleDownloadProgressListener {

  companion object {
    @JvmStatic
    fun newListener(taskId: ExternalSystemTaskId): GradleDownloadProgressListener =
      when (val project = taskId.findProject()) {
        null -> NoOpGradleDownloadProgressListener()
        else -> {
          val scopeProvider = project.service<GradleProgressCoroutineScopeProvider>()
          GradleDownloadProgressListenerImpl(taskId, project, scopeProvider.cs)
        }
      }
  }

  fun updateProgressIndicator(event: ExternalSystemTaskNotificationEvent)
  fun stopProgressIndicator(event: ExternalSystemTaskNotificationEvent)
}

@Service(Service.Level.PROJECT)
class GradleProgressCoroutineScopeProvider(val cs: CoroutineScope)

internal
class NoOpGradleDownloadProgressListener : GradleDownloadProgressListener {
  override fun updateProgressIndicator(event: ExternalSystemTaskNotificationEvent) {
  }

  override fun stopProgressIndicator(event: ExternalSystemTaskNotificationEvent) {
  }
}