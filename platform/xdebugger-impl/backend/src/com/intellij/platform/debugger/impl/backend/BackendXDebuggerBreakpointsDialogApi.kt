// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointsDialogFactory
import com.intellij.platform.debugger.impl.rpc.ShowBreakpointDialogRequest
import com.intellij.platform.debugger.impl.rpc.XDebuggerBreakpointsDialogApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

internal class BackendXDebuggerBreakpointsDialogApi : XDebuggerBreakpointsDialogApi {
  override suspend fun showDialogRequests(projectId: ProjectId): Flow<ShowBreakpointDialogRequest> {
    return channelFlow {
      BreakpointsDialogFactory.getInstance(projectId.findProject()).subscribeToShowDialogEvents(this@channelFlow) { breakpointId ->
        send(ShowBreakpointDialogRequest(breakpointId))
      }
      awaitClose()
    }
  }
}
