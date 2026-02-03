// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.xdebugger.*
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XDropFrameHandler
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.frame.XValueMarkerProvider
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.BothRunningBase
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.BothStopped
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.Exited
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.HighLevelDebuggerStepRequested
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.HighLevelPositionReached
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.HighLevelRunToAddress
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.HighRun
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.HighStarted
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.LowLevelPositionReached
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.LowLevelRunToAddress
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.LowLevelStepRequested
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.LowRun
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.MixedStepRequested
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.MixedStepType
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.PauseRequested
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.ResumeRequested
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.StepType
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.Stop
import com.intellij.xdebugger.impl.mixedmode.MixedModeStateMachineBase.HighLevelSetNextStatementRequested
import com.intellij.xdebugger.impl.ui.SessionTabComponentProvider
import com.intellij.xdebugger.impl.ui.XDebugSessionTabCustomizer
import com.intellij.xdebugger.mixedMode.XMixedModeHighLevelDebugProcessExtension
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcessExtension
import com.intellij.xdebugger.mixedMode.XMixedModeProcessHandler
import com.intellij.xdebugger.mixedMode.XMixedModeProcessesConfiguration
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import javax.swing.event.HyperlinkListener

/**
 * Interacts with XDebugSession and encapsulates the mixed mode debugging logic.
 * Primarily, it passes events from XDebugSession to the stateMachine and reacts back
 */
@ApiStatus.Internal
class XMixedModeCombinedDebugProcess(
  val low: XDebugProcess,
  val high: XDebugProcess,
  val session: XDebugSessionImpl,
  val config: XMixedModeProcessesConfiguration,
  private val stateMachine: MixedModeStateMachineBase
) : XDebugProcess(session), XDebugSessionTabCustomizer {
  private val processes = listOf(low, high)
  private var myProcessHandler: XMixedModeProcessHandler? = null
  private var editorsProvider: XMixedModeDebuggersEditorProvider? = null
  private val coroutineScope get() = session.coroutineScope
  private var myAttract : Boolean = false // being accessed on EDT
  private var highLevelDebugProcessReady : Boolean = false
  private val lowExtension get() = low.mixedModeDebugProcessExtension as XMixedModeLowLevelDebugProcessExtension
  private val highExtension get() = high.mixedModeDebugProcessExtension as XMixedModeHighLevelDebugProcessExtension
  private var positionReachedInProgress: Boolean = false
  private val stateMachineRunningJob : Job = coroutineScope.launch(Dispatchers.Default) {
    while (true) {
      when(val newState = stateMachine.stateChannel.receive()) {
        is BothStopped -> {
          val ctx = XMixedModeSuspendContext(session, newState.low, newState.high, lowExtension, stateMachine.suspendContextCoroutine)
          withContext(Dispatchers.EDT) {
            positionReachedInProgress = true
            try {
              session.positionReached(ctx, myAttract)
            }
            finally {
              positionReachedInProgress = false
              myAttract = false
            }
          }
        }
        is BothRunningBase -> {
          highLevelDebugProcessReady = true
          withContext(Dispatchers.EDT) { session.sessionResumed() }
        }
        is Exited -> break
      }
    }
  }

  /**
   * Tells XDebugSessionImpl if it has to proceed with positionReached call handling
   */
  fun handlePositionReached(suspendContext: XSuspendContext, attract: Boolean) : Boolean {
    if (!isMixedModeHighProcessReady() || positionReachedInProgress) return false

    myAttract = myAttract || attract // if any of the processes requires attraction, we'll do it
    val isHighSuspendContext = !lowExtension.belongsToMe(suspendContext)
    stateMachine.set(if (isHighSuspendContext) HighLevelPositionReached(suspendContext) else LowLevelPositionReached(suspendContext))
    return true
  }

  override fun getBreakpointHandlers(): Array<out XBreakpointHandler<*>?> = high.breakpointHandlers + low.breakpointHandlers

  override fun sessionInitialized() {
    processes.forEach { it.sessionInitialized() }
  }

  override fun startPausing() {
    if (!session.isMixedModeHighProcessReady) {
      low.startPausing()
      return
    }

    stateMachine.set(PauseRequested)
  }

  override fun startStepOver(context: XSuspendContext?) {
    if (!session.isMixedModeHighProcessReady) {
      low.startStepOver(context)
      return
    }

    mixedStepOver(context as XMixedModeSuspendContext)
  }

  // TODO
  override fun startForceStepInto(context: XSuspendContext?) {
    super.startForceStepInto(context)
  }

  override fun startStepInto(context: XSuspendContext?) {
    if (!session.isMixedModeHighProcessReady) {
      low.startStepInto(context)
      return
    }

    mixedStepInto(context as XMixedModeSuspendContext)
  }

  override fun startStepOut(context: XSuspendContext?) {
    if (!session.isMixedModeHighProcessReady) {
      low.startStepOut(context)
      return
    }

    mixedStepOut(context as XMixedModeSuspendContext)
  }

  override fun getSmartStepIntoHandler(): XSmartStepIntoHandler<*>? = getIfOnlyOneExists { it.smartStepIntoHandler }

  override fun getDropFrameHandler(): XDropFrameHandler? = getIfOnlyOneExists { it.dropFrameHandler }

  override fun getAlternativeSourceHandler(): XAlternativeSourceHandler? = getIfOnlyOneExists { it.alternativeSourceHandler }

  override fun stopAsync(): Promise<in Any> {
    stateMachine.set(Stop)

    @Suppress("UNCHECKED_CAST")
    return high.stopAsync().thenAsync { low.stopAsync() }.thenAsync { stateMachineRunningJob.asPromise() as Promise<Any> }
  }

  override fun resume(context: XSuspendContext?) {
    if (!session.isMixedModeHighProcessReady) {
      low.resume(context)
      return
    }

    stateMachine.set(ResumeRequested)
  }

  override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
    if (!session.isMixedModeHighProcessReady) {
      low.runToPosition(position, context)
      return
    }

    mixedRunToPosition(position, context as XMixedModeSuspendContext)
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

  override fun checkCanPerformCommands(): Boolean = processes.all { it.checkCanPerformCommands() }

  override fun checkCanInitBreakpoints(): Boolean = processes.all { it.checkCanInitBreakpoints() }

  override fun doGetProcessHandler(): ProcessHandler? {
    return myProcessHandler
           ?: XMixedModeProcessHandler(high.processHandler, low.processHandler, config, { stateMachine.set(MixedModeStateMachineBase.ExitingStarted) }).also { myProcessHandler = it }
  }

  override fun createConsole(): ExecutionConsole {
    return if (config.useLowDebugProcessConsole) low.createConsole() else high.createConsole()
  }

  override fun createValueMarkerProvider(): XValueMarkerProvider<*, *>? = getIfOnlyOneExists { it.createValueMarkerProvider() }

  override fun registerAdditionalActions(leftToolbar: DefaultActionGroup, topToolbar: DefaultActionGroup, settings: DefaultActionGroup) {
    low.registerAdditionalActions(leftToolbar, topToolbar, settings)
    high.registerAdditionalActions(leftToolbar, topToolbar, settings)
  }

  override fun getCurrentStateMessage(): @Nls String? = "${low.currentStateMessage}\n${high.currentStateMessage}"

  override fun getCurrentStateHyperlinkListener(): HyperlinkListener? = getIfOnlyOneExists { it.currentStateHyperlinkListener }

  override fun createTabLayouter(): XDebugTabLayouter {
    return XCombinedDebugTabLayouter(
      listOf(low.createTabLayouter(), high.createTabLayouter()),
      (if (config.useLowDebugProcessConsole) low else high).createTabLayouter())
  }


  override fun isValuesCustomSorted(): Boolean {
    if (processes.groupBy { it.isValuesCustomSorted }.count() != 1)
      error("Different custom values sorting of the debug processes is not supported")

    return processes.first().isValuesCustomSorted
  }

  override fun isLibraryFrameFilterSupported(): Boolean = processes.all { it.isLibraryFrameFilterSupported }

  override fun logStack(suspendContext: XSuspendContext, session: XDebugSession) {
    if (lowExtension.belongsToMe(suspendContext))
      low.logStack(suspendContext, session)
    else
      high.logStack(suspendContext, session)
  }

  override fun dependsOnPlugin(descriptor: IdeaPluginDescriptor): Boolean = low.dependsOnPlugin(descriptor) || high.dependsOnPlugin(descriptor)

  override fun getEditorsProvider(): XDebuggerEditorsProvider {
    return editorsProvider
           ?: XMixedModeDebuggersEditorProvider(session, low.getEditorsProvider(), high.getEditorsProvider()).also { editorsProvider = it }
  }

  override fun getBottomLocalsComponentProvider(): SessionTabComponentProvider? = getTabCustomizer()?.getBottomLocalsComponentProvider()

  override fun allowFramesViewCustomization(): Boolean = getTabCustomizer()?.allowFramesViewCustomization() == true

  override fun getDefaultFramesViewKey(): String? = getTabCustomizer()?.getDefaultFramesViewKey()

  override fun forceShowNewDebuggerUi(): Boolean = getTabCustomizer()?.forceShowNewDebuggerUi() == true

  private fun <T> getIfOnlyOneExists(selectFn: (XDebugProcess) -> T): T? {
    val handlers = processes.mapNotNull(selectFn)
    return when (handlers.size) {
      0 -> null
      1 -> handlers[0]
      else -> error("Case when both debug processes provide their own handler is not supported")
    }
  }

  private fun getTabCustomizer(): XDebugSessionTabCustomizer? = (high as? XDebugSessionTabCustomizer)
                                                                ?: (low as? XDebugSessionTabCustomizer)

  private fun mixedStepInto(suspendContext: XMixedModeSuspendContext) {
    val isLowLevelStep = !highExtension.stoppedInHighLevelSuspendContext(suspendContext)
    if (isLowLevelStep) {
      stateMachine.set(LowLevelStepRequested(suspendContext, StepType.Into))
    }
    else {
      val stepSuspendContext = suspendContext.highLevelDebugSuspendContext
      coroutineScope.launch {
        val newState =
          if (highExtension.isStepWillBringIntoLowLevelCode(stepSuspendContext))
            MixedStepRequested(stepSuspendContext, MixedStepType.IntoLowFromHigh)
          else
            HighLevelDebuggerStepRequested(stepSuspendContext, StepType.Into)

        this@XMixedModeCombinedDebugProcess.stateMachine.set(newState)
      }
    }
  }

  private fun mixedStepOver(suspendContext: XMixedModeSuspendContext) {
    val isLowLevelStep = !highExtension.stoppedInHighLevelSuspendContext(suspendContext)

    val stepType = StepType.Over
    val newState = if (isLowLevelStep) LowLevelStepRequested(suspendContext, stepType) else HighLevelDebuggerStepRequested(suspendContext.highLevelDebugSuspendContext, stepType)
    this.stateMachine.set(newState)
  }

  private fun mixedStepOut(suspendContext: XMixedModeSuspendContext) {
    val isLowLevelStep = !highExtension.stoppedInHighLevelSuspendContext(suspendContext)

    val stepType = StepType.Out
    val newState = if (isLowLevelStep) LowLevelStepRequested(suspendContext, stepType) else HighLevelDebuggerStepRequested(suspendContext.highLevelDebugSuspendContext, stepType)
    this.stateMachine.set(newState)
  }

  private fun mixedRunToPosition(position: XSourcePosition, suspendContext: XMixedModeSuspendContext) {
    val isLowLevelStep = lowExtension.belongsToMe(position.file)
    val actionSuspendContext = if (isLowLevelStep) suspendContext.lowLevelDebugSuspendContext else suspendContext.highLevelDebugSuspendContext
    val state = if (isLowLevelStep) LowLevelRunToAddress(position, actionSuspendContext) else HighLevelRunToAddress(position, actionSuspendContext)
    this.stateMachine.set(state)
  }

  fun setNextStatement(position: XSourcePosition) {
    assert(highExtension.belongsToMe(position.file)) // this operation isn't implemented for a low-level debug process
    stateMachine.set(HighLevelSetNextStatementRequested(position))
  }
}