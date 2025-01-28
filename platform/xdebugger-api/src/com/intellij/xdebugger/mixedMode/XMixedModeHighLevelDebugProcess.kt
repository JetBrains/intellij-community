// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.frame.XSuspendContext

interface XMixedModeHighLevelDebugProcess : XMixedModeDebugProcess {
  fun pauseMixedModeSession()
  suspend fun isStepWillBringIntoNativeCode(suspendContext: XSuspendContext): Boolean

  /**
   * Check that the high-level debugger supports stopping in this context
   */
  suspend fun canStopHere(lowSuspendContext: XSuspendContext): Boolean

  suspend fun refreshSuspendContextOnLowLevelStepFinish(suspendContext: XSuspendContext) : XSuspendContext?

  fun stoppedInManagedContext(suspendContext: XSuspendContext): Boolean
}