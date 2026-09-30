// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.rpc

import com.intellij.ide.rpc.awaitWithOptionalLocalFallback
import com.intellij.openapi.components.serviceOrNull
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import com.intellij.vcs.git.branch.GitInOutProjectState
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
      LiteRemoteApiProviderService.awaitWithOptionalLocalFallback(remoteApiDescriptor<GitIncomingOutgoingStateApi>()) { serviceOrNull<GitIncomingOutgoingStateApi>() }
  }
}
