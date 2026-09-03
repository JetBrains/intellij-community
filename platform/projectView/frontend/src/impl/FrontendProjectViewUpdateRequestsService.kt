// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.impl.FileUpdate
import com.intellij.platform.projectView.impl.ProjectViewUpdateRequestsService
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class FrontendProjectViewUpdateRequestsService(
  project: Project,
  scope: CoroutineScope,
) {
  companion object {
    fun getInstance(project: Project): FrontendProjectViewUpdateRequestsService = project.service()
  }

  private val channel = Channel<FileUpdate>(Channel.BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    val rpc = if (!IdeProductMode.isMonolith) {
      scope.async(CoroutineName("Waiting for the backend")) {
            ProjectViewRpc.getInstance().getFileUpdateRequestChannel(project.projectId())
        }
    }
    else {
      null
    }
    scope.launch(CoroutineName("FrontendProjectViewUpdateRequestsService")) {
      for (update in channel) {
        // First, both for monolith and light we need to send to the process-local instance.
        ProjectViewUpdateRequestsService.getInstance(project).requestUpdate(update.file, update.deep)
        // Then, if not monolith and the backend is present, send to the backend as well.
        try {
          if (rpc?.isCompleted == true) {
            rpc.await().trySend(update.toDTO())
          }
        }
        catch (e: Throwable) {
          rethrowControlFlowException(e)
          LOG.error("Could not send request to the backend", e)
        }
      }
    }
  }

  fun requestUpdate(file: VirtualFile, bool: Boolean) {
    channel.trySend(FileUpdate(file, bool))
  }
}

private val LOG = logger<FrontendProjectViewUpdateRequestsService>()
