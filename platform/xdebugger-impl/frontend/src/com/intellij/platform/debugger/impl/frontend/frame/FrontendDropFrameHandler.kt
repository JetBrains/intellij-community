// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.platform.debugger.impl.rpc.XExecutionStackApi
import com.intellij.util.ThreeState
import com.intellij.xdebugger.frame.XDropFrameHandler
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class FrontendDropFrameHandler(
  private val sessionId: XDebugSessionId,
  private val frontendSessionScope: CoroutineScope,
) : XDropFrameHandler {
  override fun canDropFrame(frame: XStackFrame): ThreeState {
    if (frame !is FrontendXStackFrame) return ThreeState.NO
    return frame.canBeDropped()
  }

  override fun drop(frame: XStackFrame) {
    if (frame !is FrontendXStackFrame || frame.canBeDropped() == ThreeState.NO) {
      return
    }
    frontendSessionScope.launch {
      XExecutionStackApi.getInstance().dropFrame(sessionId, frame.id)
    }
  }

  private fun FrontendXStackFrame.canBeDropped(): ThreeState {
    if (canDropFlow.compareAndSet(FrontendXStackFrame.CanDropState.UNSURE, FrontendXStackFrame.CanDropState.COMPUTING)) {
      frontendSessionScope.launch {
        val newState = if (XExecutionStackApi.getInstance().canDrop(sessionId, id)) {
          FrontendXStackFrame.CanDropState.YES
        }
        else {
          // TODO trust the false result, replace with NO
          FrontendXStackFrame.CanDropState.UNSURE
        }
        canDropFlow.compareAndSet(FrontendXStackFrame.CanDropState.COMPUTING, newState)
      }
    }
    return canDropFlow.value.state
  }
}
