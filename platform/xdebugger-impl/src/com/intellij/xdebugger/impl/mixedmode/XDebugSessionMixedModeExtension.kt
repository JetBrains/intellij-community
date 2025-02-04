// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.openapi.application.EDT
import com.intellij.xdebugger.XMixedModeProcessHandler
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.*
import com.intellij.xdebugger.mixedMode.XMixedModeHighLevelDebugProcess
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcess
import com.intellij.xdebugger.mixedMode.asXDebugProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.function.BiConsumer

class XDebugSessionMixedModeExtension(
  private val coroutineScope: CoroutineScope,
  private val high: XMixedModeHighLevelDebugProcess,
  private val low: XMixedModeLowLevelDebugProcess,
  private val positionReachedFn: BiConsumer<XSuspendContext, Boolean>,
) {
  private val stateMachine = MixedModeProcessTransitionStateMachine(low, high, coroutineScope)
  private val session = (high.asXDebugProcess.session as XDebugSessionImpl)
  private var editorsProvider: XMixedModeDebuggersEditorProvider? = null
  private var processHandler: XMixedModeProcessHandler? = null
  private var myAttract : Boolean = false // being accessed on EDT
  private var highLevelDebugProcessReady : Boolean = false

  init {
    coroutineScope.launch(Dispatchers.Default) {
      while (true) {
        when(val newState = stateMachine.stateChannel.receive()) {
          is BothStopped -> {
            val ctx = XMixedModeSuspendContext(session, newState.low, newState.high, low, coroutineScope)
            withContext(Dispatchers.EDT) { positionReachedFn.accept(ctx, myAttract).also { myAttract = false } }
          }
          is BothRunning -> {
            highLevelDebugProcessReady = true
            withContext(Dispatchers.EDT) { session.sessionResumed() }
          }
          is Exited -> break
        }
      }
    }
  }

  fun pause() {
    stateMachine.set(PauseRequested)
  }

  // On stop, low level debugger calls positionReached first and then the high level debugger does it
  fun positionReached(suspendContext: XSuspendContext, attract: Boolean) {
    myAttract = myAttract || attract // if any of the processes requires attraction, we'll do it
    val isHighSuspendContext = !low.belongsToMe(suspendContext)
    stateMachine.set(if (isHighSuspendContext) HighLevelPositionReached(suspendContext) else LowLevelPositionReached(suspendContext))
  }

  fun resume() {
    stateMachine.set(ResumeRequested)
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

    val event = if (isLowLevel) LowRun else HighRun
    stateMachine.set(event)
  }

  fun signalMixedModeHighProcessReady() {
    stateMachine.set(HighStarted)
  }

  fun isMixedModeHighProcessReady(): Boolean = highLevelDebugProcessReady

  fun getEditorsProvider(): XDebuggerEditorsProvider {
    return editorsProvider ?: XMixedModeDebuggersEditorProvider(session,
                                                                session.lowLevelProcessOrThrow.getEditorsProvider(),
                                                                session.highLevelProcessOrThrow.getEditorsProvider()).also { editorsProvider = it }
  }

  fun getProcessHandler() : XMixedModeProcessHandler {
    return processHandler ?: XMixedModeProcessHandler(
      high.asXDebugProcess.processHandler,
      low.asXDebugProcess.processHandler,
      checkNotNull(session.mixedModeDebugProcessOrThrow.config)).also { processHandler = it }
  }

  fun stop() {
    stateMachine.set(Stop)
  }
}