// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerValueLookupHintsRemoteApi : RemoteApi<Unit> {
  suspend fun getValueLookupListeningFlow(projectId: ProjectId): Flow<Boolean>

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerValueLookupHintsRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerValueLookupHintsRemoteApi>())
    }
  }
}
