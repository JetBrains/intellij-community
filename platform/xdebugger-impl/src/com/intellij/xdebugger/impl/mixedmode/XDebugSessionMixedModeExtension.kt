// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.*
import com.intellij.xdebugger.mixedMode.XMixedModeHighLevelDebugProcess
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcess
import com.intellij.xdebugger.mixedMode.asXDebugProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

private val logger = logger<XDebugSessionMixedModeExtension>()

class XDebugSessionMixedModeExtension(
  private val coroutineScope: CoroutineScope,
  private val high: XMixedModeHighLevelDebugProcess,
  private val low: XMixedModeLowLevelDebugProcess,
) {
  private val stateMachine = MixedModeProcessTransitionStateMachine(low, high, coroutineScope)
  private val session = (high.asXDebugProcess.session as XDebugSessionImpl)

  fun pause() {
    coroutineScope.launch {
      assert(stateMachine.get() is BothRunning)

      stateMachine.set(PauseRequested)
      stateMachine.waitFor(BothStopped::class)
    }
  }

  // On stop, low level debugger calls positionReached first and then the high level debugger does it
  fun positionReached(suspendContext: XSuspendContext, attract: Boolean): CompletableFuture<Pair<XSuspendContext, Boolean>?> =
    coroutineScope.future {
      val context = stateMachine.onPositionReached(suspendContext)
      if (context != null) {
        return@future Pair(context, attract)
      }
      return@future null
    }

  fun resume() {
    coroutineScope.launch {
      stateMachine.set(ResumeRequested)
      stateMachine.waitFor(BothRunning::class)
    }
  }

  fun stepInto(suspendContext: XMixedModeSuspendContext, stepStackFrame: XStackFrame) {
    val stepSuspendContext = getSuspendContextFromStackFrame(suspendContext, stepStackFrame)

    val isLowLevelStep = low.belongsToMe(stepStackFrame)
    if (isLowLevelStep) {
      stateMachine.set(LowLevelStepRequested(stepSuspendContext, StepType.Into))
    }
    else {
      coroutineScope.launch {
        val newState =
          if (high.isStepWillBringIntoNativeCode(stepSuspendContext))
            MixedStepRequested(stepSuspendContext, MixedStepType.IntoLowFromHigh)
          else
            HighLevelDebuggerStepRequested(stepSuspendContext, StepType.Into)

        this@XDebugSessionMixedModeExtension.stateMachine.set(newState)
      }
    }
  }

  fun stepOver(suspendContext: XMixedModeSuspendContext, stepStackFrame: XStackFrame) {
    val stepSuspendContext = getSuspendContextFromStackFrame(suspendContext, stepStackFrame)
    val isLowLevelStep = low.belongsToMe(stepStackFrame)

    val stepType = StepType.Over
    val newState = if (isLowLevelStep) LowLevelStepRequested(stepSuspendContext, stepType) else HighLevelDebuggerStepRequested(stepSuspendContext, stepType)
    this.stateMachine.set(newState)
  }

  fun stepOut(suspendContext: XMixedModeSuspendContext, stepStackFrame: XStackFrame) {
    // TODO: in unity the stepping out from native code to managed doesn't work
    //  (we got in suspend_current method when step from native call wrapper, as I understand this happens because the wrappers' nop instructions
    //  are replaced by a trampin going to suspend_current method
    val stepSuspendContext = getSuspendContextFromStackFrame(suspendContext, stepStackFrame)
    val isLowLevelStep = low.belongsToMe(stepStackFrame)

    val stepType = StepType.Out
    val newState = if (isLowLevelStep) LowLevelStepRequested(stepSuspendContext, stepType) else HighLevelDebuggerStepRequested(stepSuspendContext, stepType)
    this.stateMachine.set(newState)
  }

  fun onResumed(isLowLevel: Boolean) {
    coroutineScope.launch {
      val handled = stateMachine.onSessionResumed(isLowLevel)
      if (!handled) {
        session.sessionResumed()
      }
    }
  }

  fun signalMixedModeHighProcessReady() {
    stateMachine.set(HighStarted)
  }

  fun isMixedModeHighProcessReady(): Boolean = stateMachine.isMixedModeHighProcessReady()

  private fun getSuspendContextFromStackFrame(suspendContext: XMixedModeSuspendContext, stackFrame: XStackFrame): XSuspendContext {
    if (low.belongsToMe(stackFrame)) {
      return suspendContext.lowLevelDebugSuspendContext
    }
    else {
      return suspendContext.highLevelDebugSuspendContext
    }
  }
}