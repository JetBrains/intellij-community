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

    val result = canDrop
    // canDrop can depend on other frames in the stack,
    // and thus it can change after other stacks are loaded;
    // the hypothesis is that it can only change once and only from false to true
    if (!result) {
      frontendSessionScope.launch {
        canDrop = XDebugSessionApi.getInstance().canDrop(sessionId, id)
      }
    }
    return result
  }
}
