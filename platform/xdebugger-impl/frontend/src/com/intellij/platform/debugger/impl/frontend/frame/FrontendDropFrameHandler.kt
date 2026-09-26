// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.platform.debugger.impl.frontend.util.SequentialRpcRequestsExecutor
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.rpc.XExecutionStackApi
import com.intellij.util.ThreeState
import com.intellij.xdebugger.frame.XDropFrameHandler
import com.intellij.xdebugger.frame.XStackFrame
import kotlinx.coroutines.CoroutineScope

internal class FrontendDropFrameHandler(
  private val sessionId: XDebugSessionId,
  cs: CoroutineScope,
) : XDropFrameHandler {
  private val sequentialExecutor = SequentialRpcRequestsExecutor.create(cs)

  override fun canDropFrame(frame: XStackFrame): ThreeState {
    if (frame !is FrontendXStackFrame) return ThreeState.NO
    return frame.canBeDropped()
  }

  override fun drop(frame: XStackFrame) {
    if (frame !is FrontendXStackFrame || frame.canBeDropped() == ThreeState.NO) {
      return
    }
    sequentialExecutor.execute {
      XExecutionStackApi.getInstance().dropFrame(sessionId, frame.id)
    }
  }

  private fun FrontendXStackFrame.canBeDropped(): ThreeState {
    if (canDropFlow.compareAndSet(FrontendXStackFrame.CanDropState.UNSURE, FrontendXStackFrame.CanDropState.COMPUTING)) {
      sequentialExecutor.execute {
        val canDrop = XExecutionStackApi.getInstance().canDrop(sessionId, id)
        val newState = FrontendXStackFrame.CanDropState.fromBoolean(canDrop)
        canDropFlow.compareAndSet(FrontendXStackFrame.CanDropState.COMPUTING, newState)
      }
    }
    return canDropFlow.value.state
  }
}
