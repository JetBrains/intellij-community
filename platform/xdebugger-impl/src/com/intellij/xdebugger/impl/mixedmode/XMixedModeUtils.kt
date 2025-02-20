// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import org.jetbrains.annotations.ApiStatus

import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.mixedMode.XMixedModeHighLevelDebugProcessExtension
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcessExtension

val XDebugSession.mixedModeExecutionStack: XMixedModeExecutionStack?
  @ApiStatus.Internal
  get() = suspendContext?.activeExecutionStack as? XMixedModeExecutionStack

val XDebugSession.mixedModeExecutionStackOrThrow: XMixedModeExecutionStack
  @ApiStatus.Internal
  get() = checkNotNull(mixedModeExecutionStack)

// In mixed mode we preserve all frames in high- and low-execution stacks letting XMixedModeExecutionStack filter them
val XDebugProcess.canFilterFramesMixedModeAware: Boolean
  @ApiStatus.Internal
  get() = !session.isMixedMode || !session.isMixedModeHighProcessReady

val XDebugSession.mixedModeDebugProcessOrThrow: XMixedModeCombinedDebugProcess
  @ApiStatus.Internal
  get() = debugProcess as XMixedModeCombinedDebugProcess

val XDebugProcess.lowLevelProcessOrThrow: XDebugProcess
  @ApiStatus.Internal
  get() = checkNotNull(lowLevelProcess)

val XDebugProcess.lowLevelProcess: XDebugProcess?
  @ApiStatus.Internal
  get() = (this as? XMixedModeCombinedDebugProcess)?.low

val XDebugProcess.highLevelProcessOrThrow: XDebugProcess
  @ApiStatus.Internal
  get() = (this as XMixedModeCombinedDebugProcess).high

val XDebugSession.lowLevelProcessOrThrow: XDebugProcess
  @ApiStatus.Internal
  get() = debugProcess.lowLevelProcessOrThrow

val XDebugSession.highLevelProcessOrThrow: XDebugProcess
  @ApiStatus.Internal
  get() = debugProcess.highLevelProcessOrThrow

val XDebugProcess.lowLevelMixedModeExtensionOrThrow: XMixedModeLowLevelDebugProcessExtension
  @ApiStatus.Internal
  get() = lowLevelProcessOrThrow.mixedModeDebugProcessExtension as XMixedModeLowLevelDebugProcessExtension

val XDebugProcess.highLevelMixedModeExtensionOrThrow: XMixedModeHighLevelDebugProcessExtension
  @ApiStatus.Internal
  get() = highLevelProcessOrThrow.mixedModeDebugProcessExtension as XMixedModeHighLevelDebugProcessExtension

val XDebugSession.lowLevelMixedModeExtensionOrThrow: XMixedModeLowLevelDebugProcessExtension
  @ApiStatus.Internal
  get() = debugProcess.lowLevelMixedModeExtensionOrThrow

val XDebugSession.highLevelMixedModeExtension: XMixedModeHighLevelDebugProcessExtension
  @ApiStatus.Internal
  get() = debugProcess.highLevelMixedModeExtensionOrThrow

val XDebugSession.highLevelSuspendContext: XSuspendContext?
  @ApiStatus.Internal
  get() = suspendContext?.highLevel

val XDebugSession.lowLevelSuspendContext: XSuspendContext?
  @ApiStatus.Internal
  get() = suspendContext?.lowLevel

val XSuspendContext.highLevel: XSuspendContext?
  @ApiStatus.Internal
  get() = (this as? XMixedModeSuspendContext)?.highLevelDebugSuspendContext

val XSuspendContext.lowLevel: XSuspendContext?
  @ApiStatus.Internal
  get() = (this as? XMixedModeSuspendContext)?.lowLevelDebugSuspendContext

@ApiStatus.Internal
fun XDebugSession.getLowLevelFrame(): XStackFrame? {
  assert(isMixedMode)
  val stack = this.mixedModeExecutionStack ?: return null
  if (!stack.computedFramesMap.isCompleted) return null
  return stack.computedFramesMap.getCompleted().entries.firstOrNull { it.value == currentStackFrame }?.key
}

@ApiStatus.Internal
fun XDebugSession.signalMixedModeHighProcessReady() {
  assert(isMixedMode)
  (debugProcess as XMixedModeCombinedDebugProcess).signalMixedModeHighProcessReady()
}

val XDebugSession.isMixedModeHighProcessReady: Boolean
  @ApiStatus.Internal
  get() = run {
    assert(isMixedMode)
    (debugProcess as XMixedModeCombinedDebugProcess).isMixedModeHighProcessReady()
  }

@ApiStatus.Internal
fun XDebugSession.mixedModeSessionResumed(isLowLevelDebugger: Boolean) {
  assert(isMixedMode)
  (debugProcess as XMixedModeCombinedDebugProcess).onResumed(isLowLevelDebugger)
}