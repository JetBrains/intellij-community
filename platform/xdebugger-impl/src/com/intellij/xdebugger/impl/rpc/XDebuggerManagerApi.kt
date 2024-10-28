// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerManagerApi : RemoteApi<Unit> {
  suspend fun currentSession(projectId: ProjectId): Flow<XDebugSessionId?>

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerManagerApi {
      return withKernel {
        RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerManagerApi>())
      }
    }
  }
}