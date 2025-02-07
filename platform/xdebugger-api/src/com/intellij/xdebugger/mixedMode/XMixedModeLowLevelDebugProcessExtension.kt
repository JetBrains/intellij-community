// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import com.intellij.xdebugger.frame.XMixedModeSuspendContextBase
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.Deferred

interface XMixedModeLowLevelDebugProcessExtension : XMixedModeDebugProcessExtension {
  val ready : Deferred<Unit>
  val mixedStackBuilder: MixedModeStackBuilder

  suspend fun continueAllThreads(exceptThreads: Set<Long>, silent : Boolean)
  suspend fun handleBreakpointDuringStep()

  fun pauseMixedModeSession(stopEventThreadId: Long)
  suspend fun startMixedStepInto(steppingThreadId: Long, ctx: XSuspendContext): Int
  suspend fun finishMixedStepInto()
  fun lowToHighTransitionDuringLastStepHappened() : Boolean

  suspend fun beforeStep(mixedSuspendContext: XMixedModeSuspendContextBase)
  fun belongsToMe(context: XSuspendContext) : Boolean
}