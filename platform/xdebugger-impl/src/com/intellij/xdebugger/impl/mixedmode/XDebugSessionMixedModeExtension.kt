// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.XMixedModeProcessHandler
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XMixedModeDebuggersEditorProvider
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
  private var editorsProvider: XMixedModeDebuggersEditorProvider? = null
  private var processHandler: XMixedModeProcessHandler? = null

  fun pause() {
    coroutineScope.launch {
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

  fun stepInto(suspendContext: XMixedModeSuspendContext) {
    val isLowLevelStep = !high.stoppedInManagedContext(suspendContext)
    if (isLowLevelStep) {
      stateMachine.set(LowLevelStepRequested(suspendContext, StepType.Into))
    }
    else {
      val stepSuspendContext = suspendContext.highLevelDebugSuspendContext
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

  fun stepOver(suspendContext: XMixedModeSuspendContext) {
    val isLowLevelStep = !high.stoppedInManagedContext(suspendContext)

    val stepType = StepType.Over
    val newState = if (isLowLevelStep) LowLevelStepRequested(suspendContext, stepType) else HighLevelDebuggerStepRequested(suspendContext.highLevelDebugSuspendContext, stepType)
    this.stateMachine.set(newState)
  }

  fun stepOut(suspendContext: XMixedModeSuspendContext) {
    val isLowLevelStep = !high.stoppedInManagedContext(suspendContext)

    val stepType = StepType.Out
    val newState = if (isLowLevelStep) LowLevelStepRequested(suspendContext, stepType) else HighLevelDebuggerStepRequested(suspendContext.highLevelDebugSuspendContext, stepType)
    this.stateMachine.set(newState)
  }

  fun runToPosition(position: XSourcePosition, suspendContext: XMixedModeSuspendContext) {
    val isLowLevelStep = low.belongsToMe(position.file)
    val actionSuspendContext = if (isLowLevelStep) suspendContext.lowLevelDebugSuspendContext else suspendContext.highLevelDebugSuspendContext
    val state = if (isLowLevelStep) LowLevelRunToAddress(position, actionSuspendContext) else HighLevelRunToAddress(position, actionSuspendContext)
    this.stateMachine.set(state)
  }

  fun onResumed(isLowLevel: Boolean) {
    if (!session.isMixedModeHighProcessReady) {
      session.sessionResumed()
      return
    }

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

  fun getEditorsProvider(): XDebuggerEditorsProvider {
    return editorsProvider ?: XMixedModeDebuggersEditorProvider(session,
                                                                session.getDebugProcess(true).getEditorsProvider(),
                                                                session.getDebugProcess(false).getEditorsProvider()).also { editorsProvider = it }
  }

  fun getProcessHandler() : XMixedModeProcessHandler {
    return processHandler ?: XMixedModeProcessHandler(
      high.asXDebugProcess.processHandler,
      low.asXDebugProcess.processHandler,
      checkNotNull(session.mixedModeConfig)).also { processHandler = it }
  }

  fun stop() {
    stateMachine.set(Stop)
  }
}