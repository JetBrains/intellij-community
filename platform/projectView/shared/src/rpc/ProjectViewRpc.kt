// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.projectView.pane.ProjectViewPaneDescriptor
import com.intellij.platform.projectView.pane.ProjectViewPaneId
import com.intellij.platform.projectView.pane.ProjectViewPaneProviderId
import com.intellij.platform.projectView.pane.ProjectViewPaneRequest
import com.intellij.platform.projectView.pane.ProjectViewPaneStateEventDTO
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface ProjectViewRpc : RemoteApi<Unit> {
  suspend fun getPaneRequestChannel(projectId: ProjectId, providerId: ProjectViewPaneProviderId, paneId: ProjectViewPaneId): SendChannel<ProjectViewPaneRequest>
  
  suspend fun getPaneDescriptors(projectId: ProjectId, providerId: ProjectViewPaneProviderId): List<ProjectViewPaneDescriptor>

  suspend fun getPaneStateFlow(projectId: ProjectId, providerId: ProjectViewPaneProviderId, paneId: ProjectViewPaneId): Flow<ProjectViewPaneStateEventDTO>
  
  companion object {
    suspend fun getInstance(): ProjectViewRpc = RemoteApiProviderService.resolve(remoteApiDescriptor<ProjectViewRpc>())
  }
}
