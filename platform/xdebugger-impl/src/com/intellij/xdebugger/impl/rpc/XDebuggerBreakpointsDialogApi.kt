// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerBreakpointsDialogApi : RemoteApi<Unit> {
  // TODO: backend shouldn't make requests to show breakpoints dialog
  //   but it is required now, since some BEControls may trigger breakpoints dialog showing
  //   it means that backend should request frontend.
  //   When all the be controls and LUX will be split, we can trigger this dialog showing on the frontend side.
  // TODO: pass current breakpoint through this Flow of requests
  // TODO: pass clientId, otherwise dialog will be shown for all clients in CWM
  suspend fun showDialogRequests(projectId: ProjectId): Flow<ShowBreakpointDialogRequest>

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerBreakpointsDialogApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerBreakpointsDialogApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
data class ShowBreakpointDialogRequest(val breakpointId: XBreakpointId?)