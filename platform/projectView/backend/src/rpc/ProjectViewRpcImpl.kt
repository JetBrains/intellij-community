// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.backend.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.projectView.backend.pane.BackendProjectViewPaneService
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneProviderId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEventDTO
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
    providerId: ProjectViewPaneProviderId,
    paneId: ProjectViewPaneId,
  ): SendChannel<ProjectViewPaneRequest> {
    return BackendProjectViewPaneService.getInstanceSuspend(projectId.findProject()).getPaneRequestChannel(providerId, paneId)
  }

  override suspend fun getPaneDescriptors(
    projectId: ProjectId,
    providerId: ProjectViewPaneProviderId,
  ): List<ProjectViewPaneDescriptor> {
    return BackendProjectViewPaneService.getInstanceSuspend(projectId.findProject()).getPaneDescriptors(providerId)
  }

  override suspend fun getPaneStateFlow(
    projectId: ProjectId,
    providerId: ProjectViewPaneProviderId,
    paneId: ProjectViewPaneId,
  ): Flow<ProjectViewPaneStateEventDTO> {
    return BackendProjectViewPaneService.getInstanceSuspend(projectId.findProject()).getPaneStateFlow(providerId, paneId).map {
      it.toDTO()
    }
  }
}
