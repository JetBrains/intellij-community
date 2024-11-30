// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.frame.XSuspendContext

interface XMixedModeHighLevelDebugProcess : XMixedModeDebugProcess {
  fun getFramesMatcher(): MixedModeFramesBuilder
  suspend fun triggerBringingManagedThreadsToUnBlockedState()
  fun pauseMixedModeSession()
  suspend fun isStepWillBringIntoNativeCode(suspendContext: XSuspendContext): Boolean

  // Should be moved to mono specific class
  suspend fun triggerMonoMethodCommandsInternalMethodCallForExternMethodWeStepIn(suspendContext: XSuspendContext)
}