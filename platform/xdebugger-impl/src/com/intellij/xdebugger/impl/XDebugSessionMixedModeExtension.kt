// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.EDT
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessDebuggeeInForeground
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.MixedModeProcessTransitionStateMachine.BothRunning
import com.intellij.xdebugger.impl.MixedModeProcessTransitionStateMachine.BothStopped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture
import kotlin.jvm.javaClass

private val logger = com.intellij.openapi.diagnostic.logger<XDebugProcessDebuggeeInForeground>()

abstract class XDebugSessionMixedModeExtension(
  private val coroutineScope: CoroutineScope,
  private val high: XDebugProcess,
  private val low: XDebugProcess,
) {
  private val stateMachine = MixedModeProcessTransitionStateMachine(low as XMixedModeDebugProcess, high as XMixedModeDebugProcess, coroutineScope)
  private val session = (high.session as XDebugSessionImpl)


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
      stateMachine.set(MixedModeProcessTransitionStateMachine.ResumeRequested)
      stateMachine.waitFor(BothRunning::class)
    }
  }

  fun stepInto(suspendContext: XSuspendContext) {
    if (isLowSuspendContext(suspendContext)) {
      this.low.startStepInto(suspendContext)
    }
    else
      TODO("not yet supported")
  }

  fun stepOver(suspendContext: XSuspendContext) {
    if (isLowSuspendContext(suspendContext)) {
      this.low.startStepOver(suspendContext)
    }
    else {
      this.stateMachine.set(MixedModeProcessTransitionStateMachine.HighLevelDebuggerStepOverRequested(suspendContext))
    }
  }

  fun stepOut(suspendContext: XSuspendContext) {
    if (isLowSuspendContext(suspendContext)) {
      this.low.startStepOut(suspendContext)
    }
    else
      TODO("not yet supported")
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


class MonoXDebugSessionMixedModeExtension(coroutineScope: CoroutineScope, high: XDebugProcess, low: XDebugProcess)
  : XDebugSessionMixedModeExtension(coroutineScope, high, low) {
  override fun isLowSuspendContext(suspendContext: XSuspendContext): Boolean {
    return suspendContext.javaClass.name.contains("Cidr")
  }

  override fun isLowStackFrame(stackFrame: XStackFrame): Boolean {
    return stackFrame.javaClass.name.contains("Cidr")
  }
}