// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.rpc

import com.intellij.ide.rpc.awaitWithOptionalLocalFallback
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.components.serviceOrNull
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface GitWidgetApi : RemoteApi<Unit> {
  suspend fun getWidgetState(projectId: ProjectId, selectedFile: VirtualFileId?): Flow<GitWidgetState>

  companion object {
    suspend fun getInstance(): GitWidgetApi = LiteRemoteApiProviderService.awaitWithOptionalLocalFallback(remoteApiDescriptor<GitWidgetApi>()) { serviceOrNull<GitWidgetApi>() }
  }
}