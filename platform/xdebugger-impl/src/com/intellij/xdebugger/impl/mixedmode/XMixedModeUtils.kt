// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession

val XDebugSession.mixedModeExecutionStack: XMixedModeExecutionStack?
  get() = suspendContext?.activeExecutionStack as? XMixedModeExecutionStack

val XDebugSession.mixedModeExecutionStackOrThrow: XMixedModeExecutionStack
  get() = checkNotNull(mixedModeExecutionStack)

// In mixed mode we preserve all frames in high- and low- execution stacks letting XMixedModeExecutionStack filter them
val XDebugProcess.canFilterFramesMixedModeAware: Boolean
  get() = !session.isMixedMode || !session.isMixedModeHighProcessReady

val XDebugSession.mixedModeDebugProcessOrThrow: XMixedModeCombinedDebugProcess
  get() = debugProcess as XMixedModeCombinedDebugProcess
