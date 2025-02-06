// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.mixedMode.XMixedModeHighLevelDebugProcess
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcess
import kotlinx.coroutines.ExperimentalCoroutinesApi

val XDebugSession.mixedModeExecutionStack: XMixedModeExecutionStack?
  get() = suspendContext?.activeExecutionStack as? XMixedModeExecutionStack

val XDebugSession.mixedModeExecutionStackOrThrow: XMixedModeExecutionStack
  get() = checkNotNull(mixedModeExecutionStack)

// In mixed mode we preserve all frames in high- and low- execution stacks letting XMixedModeExecutionStack filter them
val XDebugProcess.canFilterFramesMixedModeAware: Boolean
  get() = !session.isMixedMode || !session.isMixedModeHighProcessReady

val XDebugSession.mixedModeDebugProcessOrThrow: XMixedModeCombinedDebugProcess
  get() = debugProcess as XMixedModeCombinedDebugProcess

val XDebugProcess.lowLevelProcessOrThrow: XDebugProcess
  get() = checkNotNull(lowLevelProcess)

val XDebugProcess.lowLevelProcess: XDebugProcess?
  get() = (this as? XMixedModeCombinedDebugProcess)?.low

val XDebugProcess.highLevelProcessOrThrow: XDebugProcess
  get() = (this as XMixedModeCombinedDebugProcess).high

val XDebugSession.lowLevelProcessOrThrow: XDebugProcess
  get() = debugProcess.lowLevelProcessOrThrow

val XDebugSession.highLevelProcessOrThrow: XDebugProcess
  get() = debugProcess.highLevelProcessOrThrow

val XDebugProcess.lowLevelMixedModeExtensionOrThrow: XMixedModeLowLevelDebugProcess
  get() = lowLevelProcessOrThrow as XMixedModeLowLevelDebugProcess

val XDebugProcess.highLevelMixedModeExtensionOrThrow: XMixedModeHighLevelDebugProcess
  get() = highLevelProcessOrThrow as XMixedModeHighLevelDebugProcess

val XDebugSession.lowLevelMixedModeExtensionOrThrow: XMixedModeLowLevelDebugProcess
  get() = debugProcess.lowLevelMixedModeExtensionOrThrow

val XDebugSession.highLevelMixedModeExtension: XMixedModeHighLevelDebugProcess
  get() = debugProcess.highLevelMixedModeExtensionOrThrow

val XDebugSession.highLevelSuspendContext: XSuspendContext?
  get() = suspendContext?.highLevel

val XDebugSession.lowLevelSuspendContext: XSuspendContext?
  get() = suspendContext?.lowLevel

val XSuspendContext.highLevel: XSuspendContext?
  get() = (this as? XMixedModeSuspendContext)?.highLevelDebugSuspendContext

val XSuspendContext.lowLevel: XSuspendContext?
  get() = (this as? XMixedModeSuspendContext)?.lowLevelDebugSuspendContext

@OptIn(ExperimentalCoroutinesApi::class)
fun XDebugSession.getLowLevelFrame(): XStackFrame? {
  assert(isMixedMode)
  val stack = this.mixedModeExecutionStack ?: return null
  if (!stack.computedFramesMap.isCompleted) return null
  return stack.computedFramesMap.getCompleted().entries.firstOrNull { it.value == currentStackFrame }?.key
}

fun XDebugSession.signalMixedModeHighProcessReady() {
  assert(isMixedMode)
  (debugProcess as XMixedModeCombinedDebugProcess).signalMixedModeHighProcessReady()
}

val XDebugSession.isMixedModeHighProcessReady: Boolean
  get() = run {
    assert(isMixedMode)
    (debugProcess as XMixedModeCombinedDebugProcess).isMixedModeHighProcessReady()
  }

fun XDebugSession.mixedModeSessionResumed(isLowLevelDebugger: Boolean) {
  assert(isMixedMode)
  (debugProcess as XMixedModeCombinedDebugProcess).onResumed(isLowLevelDebugger)
}