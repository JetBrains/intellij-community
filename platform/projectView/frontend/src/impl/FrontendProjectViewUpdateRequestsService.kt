// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.frontend.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.project.projectId
import com.intellij.platform.projectView.impl.FileUpdate
import com.intellij.platform.projectView.impl.ProjectViewUpdateRequestsService
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class FrontendProjectViewUpdateRequestsService(
  project: Project,
  scope: CoroutineScope,
) {
  companion object {
    fun getInstance(project: Project): FrontendProjectViewUpdateRequestsService = project.service()
  }

  private val updates = MutableSharedFlow<FileUpdate>(
    replay = 0,
    extraBufferCapacity = 1000,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
  )

  init {
    scope.launch(
      CoroutineName("Frontend PV updates: monolith or light"),
      CoroutineStart.UNDISPATCHED, // ensure the collector is started immediately
    ) {
      updates.collect { update ->
        ProjectViewUpdateRequestsService.getInstance(project).requestUpdate(update.file, update.deep)
      }
    }
    if (!IdeProductMode.isMonolith) {
      scope.launch(
        CoroutineName("Frontend PV updates: sending to the backend"),
        // no need for UNDISPATCHED here, this thing won't start instantly anyway
      ) {
        val rpcChannel = ProjectViewRpc.getInstance().getFileUpdateRequestChannel(project.projectId())
        updates.collect { update ->
          rpcChannel.send(update.toDTO())
        }
      }
    }
  }

  fun requestUpdate(file: VirtualFile, bool: Boolean) {
    check(updates.tryEmit(FileUpdate(file, bool)))
  }
}
