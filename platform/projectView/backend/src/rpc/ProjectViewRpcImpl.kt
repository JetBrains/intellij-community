// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.projectView.actions.EditorChoice
import com.intellij.platform.projectView.backend.pane.BackendProjectViewPaneService
import com.intellij.platform.projectView.pane.ProjectViewNodePathImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptorImpl
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEventDTO
import com.intellij.platform.projectView.pane.SelectInRequestDTO
import com.intellij.platform.projectView.rpc.ProjectViewRpc
import com.intellij.platform.rpc.backend.RemoteApiProvider
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class ProjectViewRpcProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ProjectViewRpc>()) {
      ProjectViewRpcImpl()
    }
  }
}

internal class ProjectViewRpcImpl : ProjectViewRpc {
  override suspend fun getPaneRequestChannel(
    projectId: ProjectId,
    paneId: ProjectViewPaneId,
  ): SendChannel<ProjectViewPaneRequest> {
    return BackendProjectViewPaneService.getInstance(projectId.findProject()).getPaneRequestChannel(paneId)
  }

  override suspend fun getPaneDescriptorsFlow(
    projectId: ProjectId,
  ): Flow<List<ProjectViewPaneDescriptorImpl>> {
    return BackendProjectViewPaneService.getInstance(projectId.findProject()).getPaneDescriptorsFlow()
  }

  override suspend fun getPaneStateFlow(
    projectId: ProjectId,
    paneId: ProjectViewPaneId,
  ): Flow<ProjectViewPaneStateEventDTO> {
    return BackendProjectViewPaneService.getInstance(projectId.findProject()).getPaneStateFlow(paneId).map {
      it.toDTO()
    }
  }

  override suspend fun findNodeForOpenedFile(
    projectId: ProjectId,
    paneId: ProjectViewPaneId,
    editorChoice: EditorChoice,
    isInvokedManually: Boolean,
  ): ProjectViewNodePathImpl? {
    return BackendProjectViewPaneService.getInstance(projectId.findProject()).findNodeForOpenedFile(paneId, editorChoice, isInvokedManually) as ProjectViewNodePathImpl?
  }

  override suspend fun findNodeForSelectIn(
      projectId: ProjectId,
      selectInRequest: SelectInRequestDTO,
  ): ProjectViewNodePathImpl? {
    return BackendProjectViewPaneService.getInstance(projectId.findProject()).findNodeForSelectIn(selectInRequest) as ProjectViewNodePathImpl?
  }
}
