// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.EDT
import com.intellij.xdebugger.XDebugProcessDebuggeeInForeground
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.MixedModeProcessTransitionStateMachine.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

private val logger = com.intellij.openapi.diagnostic.logger<XDebugProcessDebuggeeInForeground>()

abstract class XDebugSessionMixedModeExtension(
  private val coroutineScope: CoroutineScope,
  private val high: XMixedModeHighLevelDebugProcess,
  private val low: XMixedModeLowLevelDebugProcess,
) {
  private val stateMachine = MixedModeProcessTransitionStateMachine(low, high, coroutineScope)
  private val session = (high.asXDebugProcess.session as XDebugSessionImpl)


  abstract fun isLowSuspendContext(suspendContext: XSuspendContext): Boolean
  abstract fun isLowStackFrame(stackFrame: XStackFrame): Boolean

  fun pause() {
    coroutineScope.launch(Dispatchers.EDT) {
      assert(stateMachine.get() is BothRunning)

      stateMachine.set(MixedModeProcessTransitionStateMachine.StopRequested)
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
    coroutineScope.launch(Dispatchers.EDT) {
      stateMachine.set(ResumeRequested)
      stateMachine.waitFor(BothRunning::class)
    }
  }

  fun stepInto(suspendContext: XSuspendContext) {
    if (isLowSuspendContext(suspendContext)) {
      stateMachine.set(LowLevelStepRequested(suspendContext, StepType.Into))
    }
    else {
      coroutineScope.launch(Dispatchers.EDT) {
        val newState =
          if (high.isStepWillBringIntoNativeCode(suspendContext))
            MixedStepRequested(suspendContext, MixedStepType.IntoLowFromHigh)
          else
            HighLevelDebuggerStepRequested(suspendContext, StepType.Into)

        this@XDebugSessionMixedModeExtension.stateMachine.set(newState)
      }
    }
  }

  fun stepOver(suspendContext: XSuspendContext) {
    val stepType = StepType.Over
    val newState = if (isLowSuspendContext(suspendContext)) LowLevelStepRequested(suspendContext, stepType) else HighLevelDebuggerStepRequested(suspendContext, stepType)
    this.stateMachine.set(newState)
  }

  fun stepOut(suspendContext: XSuspendContext) {
    val stepType = StepType.Out
    val newState = if (isLowSuspendContext(suspendContext)) LowLevelStepRequested(suspendContext, stepType) else HighLevelDebuggerStepRequested(suspendContext, stepType)
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
}


class MonoXDebugSessionMixedModeExtension(coroutineScope: CoroutineScope, high: XMixedModeHighLevelDebugProcess, low: XMixedModeLowLevelDebugProcess)
  : XDebugSessionMixedModeExtension(coroutineScope, high, low) {
  override fun isLowSuspendContext(suspendContext: XSuspendContext): Boolean {
    return suspendContext.javaClass.name.contains("Cidr")
  }

  override fun isLowStackFrame(stackFrame: XStackFrame): Boolean {
    return stackFrame.javaClass.name.contains("Cidr")
  }
}