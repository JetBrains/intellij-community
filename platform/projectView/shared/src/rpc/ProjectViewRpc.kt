// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.pane.ProjectViewNodePathImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEventDTO
import com.intellij.platform.projectView.pane.SelectInRequestDTO
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface ProjectViewRpc : RemoteApi<Unit> {
  suspend fun getPaneRequestChannel(projectId: ProjectId, paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest>
  
  suspend fun getPaneDescriptorsFlow(projectId: ProjectId): Flow<List<ProjectViewPaneDescriptorImpl>>

  suspend fun getPaneStateFlow(projectId: ProjectId, paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEventDTO>

  suspend fun findNodeForOpenedFile(projectId: ProjectId, paneId: ProjectViewPaneId, editorChoice: EditorChoice, isInvokedManually: Boolean): ProjectViewNodePathImpl?

  suspend fun findNodeForSelectIn(projectId: ProjectId, selectInRequest: SelectInRequestDTO): ProjectViewNodePathImpl?

  companion object {
    suspend fun getInstance(): ProjectViewRpc = LiteRemoteApiProviderService.awaitConnectionAndResolve(remoteApiDescriptor<ProjectViewRpc>())
  }
}
