// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.Deferred

interface XMixedModeLowLevelDebugProcess : XMixedModeDebugProcess {
  val ready : Deferred<Unit>

  suspend fun continueAllThreads(exceptThreads: Set<Long>, silent : Boolean)
  suspend fun continueHighDebuggerServiceThreads()

  fun pauseMixedModeSession(stopEventThreadId: Long)
  suspend fun startMixedStepInto(steppingThreadId: Long, ctx: XSuspendContext): Int
  suspend fun removeTempBreakpoint(brId: Int)
  fun lowToHighTransitionDuringLastStepHappened() : Boolean
}