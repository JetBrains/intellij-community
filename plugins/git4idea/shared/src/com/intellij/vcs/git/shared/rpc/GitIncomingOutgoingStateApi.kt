// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.vcs.git.shared.branch.GitInOutProjectState
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface GitIncomingOutgoingStateApi : RemoteApi<Unit> {
  suspend fun syncState(projectId: ProjectId): Flow<GitInOutProjectState>

  companion object {
    suspend fun getInstance(): GitIncomingOutgoingStateApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<GitIncomingOutgoingStateApi>())
  }
}
