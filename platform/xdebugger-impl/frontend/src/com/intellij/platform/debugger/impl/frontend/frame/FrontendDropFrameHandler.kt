// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.xdebugger.frame.XDropFrameHandler
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal class FrontendDropFrameHandler(private val sessionId: XDebugSessionId,
                                        private val frontendSessionScope: CoroutineScope) : XDropFrameHandler {
  override fun canDrop(frame: XStackFrame): Boolean {
    return frame.canBeDropped()
  }

  override fun drop(frame: XStackFrame) {
    if (!frame.canBeDropped()) {
      return
    }
    frontendSessionScope.launch {
      XDebugSessionApi.getInstance().dropFrame(sessionId, frame.id)
    }
  }

  // just for the smart cast
  @OptIn(ExperimentalContracts::class)
  private fun XStackFrame.canBeDropped(): Boolean {
    contract {
      returns(true) implies (this@canBeDropped is FrontendXStackFrame)
    }
    if (this !is FrontendXStackFrame) {
      return false
    }

    if (canDropFlow.compareAndSet(FrontendXStackFrame.CanDropState.UNSURE, FrontendXStackFrame.CanDropState.COMPUTING)) {
      frontendSessionScope.launch {
        val newState = if (XDebugSessionApi.getInstance().canDrop(sessionId, id)) {
          FrontendXStackFrame.CanDropState.CAN_DROP
        }
        else {
          FrontendXStackFrame.CanDropState.UNSURE
        }
        canDropFlow.compareAndSet(FrontendXStackFrame.CanDropState.COMPUTING, newState)
      }
    }
    return canDropFlow.value.canDrop
  }
}
