// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.*
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XDropFrameHandler
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.frame.XValueMarkerProvider
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.BothRunning
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.BothStopped
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.Exited
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.HighLevelDebuggerStepRequested
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.HighLevelPositionReached
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.HighLevelRunToAddress
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.HighRun
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.HighStarted
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.LowLevelPositionReached
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.LowLevelRunToAddress
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.LowLevelStepRequested
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.LowRun
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.MixedStepRequested
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.MixedStepType
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.PauseRequested
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.ResumeRequested
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.StepType
import com.intellij.xdebugger.impl.mixedmode.MixedModeProcessTransitionStateMachine.Stop
import com.intellij.xdebugger.impl.ui.SessionTabComponentProvider
import com.intellij.xdebugger.impl.ui.XDebugSessionTabCustomizer
import com.intellij.xdebugger.mixedMode.XMixedModeHighLevelDebugProcessExtension
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcessExtension
import com.intellij.xdebugger.mixedMode.XMixedModeProcessesConfiguration
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.Promise
import javax.swing.event.HyperlinkListener

private val LOG = logger<XMixedModeCombinedDebugProcess>()

class XMixedModeCombinedDebugProcess(
  val low: XDebugProcess,
  val high: XDebugProcess,
  val session: XDebugSessionImpl,
  val config: XMixedModeProcessesConfiguration,
) : XDebugProcess(session), XDebugSessionTabCustomizer {
  private val processes = listOf(low, high)
  private var myProcessHandler: XMixedModeProcessHandler? = null
  private var editorsProvider: XMixedModeDebuggersEditorProvider? = null
  private val coroutineScope get() = session.coroutineScope
  private val stateMachine = MixedModeProcessTransitionStateMachine(low, high, coroutineScope)
  private var myAttract : Boolean = false // being accessed on EDT
  private var highLevelDebugProcessReady : Boolean = false
  private val lowExtension get() = low.mixedModeDebugProcessExtension as XMixedModeLowLevelDebugProcessExtension
  private val highExtension get() = high.mixedModeDebugProcessExtension as XMixedModeHighLevelDebugProcessExtension
  private var positionReachedInProgress: Boolean = false
  init {
    coroutineScope.launch(Dispatchers.Default) {
      while (true) {
        when(val newState = stateMachine.stateChannel.receive()) {
          is BothStopped -> {
            val ctx = XMixedModeSuspendContext(session, newState.low, newState.high, lowExtension, coroutineScope)
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
          is BothRunning -> {
            highLevelDebugProcessReady = true
            withContext(Dispatchers.EDT) { session.sessionResumed() }
          }
          is Exited -> break
        }
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
    stateMachine.set(PauseRequested)
  }

  // not called from debug session
  override fun startStepOver() {
    super.startStepOver()
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

  // not called from debug session
  override fun startStepInto() {
    super.startStepInto()
  }

  override fun startStepInto(context: XSuspendContext?) {
    if (!session.isMixedModeHighProcessReady) {
      low.startStepInto(context)
      return
    }

    mixedStepInto(context as XMixedModeSuspendContext)
  }

  // not called from debug session
  override fun startStepOut() {
    super.startStepOut()
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

  // not called from debug session
  override fun stop() {
    super.stop()
  }

  override fun stopAsync(): Promise<in Any> {
    stateMachine.set(Stop)
    return high.stopAsync().thenAsync { low.stopAsync() }
  }

  // not called from debug session
  override fun resume() {
    super.resume()
  }

  override fun resume(context: XSuspendContext?) {
    if (!session.isMixedModeHighProcessReady) {
      low.resume(context)
      return
    }

    stateMachine.set(ResumeRequested)
  }

  // not called from debug session
  override fun runToPosition(position: XSourcePosition) {
    super.runToPosition(position)
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
    return myProcessHandler ?: XMixedModeProcessHandler(high.processHandler, low.processHandler, config).also { myProcessHandler = it }
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
    if (!processes.all { it.isValuesCustomSorted })
      error("Custom values sorting only for one debug process is not yet supported")

    return super.isValuesCustomSorted()
  }

  // satisfied with the super class implementation, that chooses evaluator depending on a current selected stack frame
  override fun getEvaluator(): XDebuggerEvaluator? {
    return super.getEvaluator()
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
    val isLowLevelStep = !highExtension.stoppedInManagedContext(suspendContext)
    if (isLowLevelStep) {
      stateMachine.set(LowLevelStepRequested(suspendContext, StepType.Into))
    }
    else {
      val stepSuspendContext = suspendContext.highLevelDebugSuspendContext
      coroutineScope.launch {
        val newState =
          if (highExtension.isStepWillBringIntoNativeCode(stepSuspendContext))
            MixedStepRequested(stepSuspendContext, MixedStepType.IntoLowFromHigh)
          else
            HighLevelDebuggerStepRequested(stepSuspendContext, StepType.Into)

        this@XMixedModeCombinedDebugProcess.stateMachine.set(newState)
      }
    }
  }

  private fun mixedStepOver(suspendContext: XMixedModeSuspendContext) {
    val isLowLevelStep = !highExtension.stoppedInManagedContext(suspendContext)

    val stepType = StepType.Over
    val newState = if (isLowLevelStep) LowLevelStepRequested(suspendContext, stepType) else HighLevelDebuggerStepRequested(suspendContext.highLevelDebugSuspendContext, stepType)
    this.stateMachine.set(newState)
  }

  private fun mixedStepOut(suspendContext: XMixedModeSuspendContext) {
    val isLowLevelStep = !highExtension.stoppedInManagedContext(suspendContext)

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
}