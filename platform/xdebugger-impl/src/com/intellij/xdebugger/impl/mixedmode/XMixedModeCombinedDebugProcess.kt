// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.mixedmode

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.*
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.evaluation.XMixedModeDebuggersEditorProvider
import com.intellij.xdebugger.frame.XDropFrameHandler
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.frame.XValueMarkerProvider
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.SessionTabComponentProvider
import com.intellij.xdebugger.impl.ui.XDebugSessionTabCustomizer
import com.intellij.xdebugger.mixedMode.XMixedModeHighLevelDebugProcess
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcess
import com.intellij.xdebugger.mixedMode.XMixedModeProcessesConfiguration
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.ui.XDebugTabLayouter
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.Promise
import javax.swing.event.HyperlinkListener

private val LOG = logger<XMixedModeCombinedDebugProcess>()

class XMixedModeCombinedDebugProcess(
  val low: XDebugProcess,
  val high: XDebugProcess,
  session: XDebugSession,
  val config: XMixedModeProcessesConfiguration,
) : XDebugProcess(session), XDebugSessionTabCustomizer {
  private val processes = listOf(low, high)
  private var myProcessHandler: XMixedModeProcessHandler? = null
  private var editorsProvider: XMixedModeDebuggersEditorProvider? = null
  val extension: XDebugSessionMixedModeExtension = XDebugSessionMixedModeExtension((session as XDebugSessionImpl).coroutineScope,
                                                                                   high as XMixedModeHighLevelDebugProcess,
                                                                                   low as XMixedModeLowLevelDebugProcess,
                                                                                   session::positionReachedInternal)

  override fun getBreakpointHandlers(): Array<out XBreakpointHandler<*>?> = high.breakpointHandlers + low.breakpointHandlers

  override fun sessionInitialized() {
    processes.forEach { it.sessionInitialized() }
  }

  override fun startPausing() {
    extension.pause()
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

    extension.stepOver(context as XMixedModeSuspendContext)
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

    extension.stepInto(context as XMixedModeSuspendContext)
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

    extension.stepOut(context as XMixedModeSuspendContext)
  }

  override fun getSmartStepIntoHandler(): XSmartStepIntoHandler<*>? = getIfOnlyOneExists { it.smartStepIntoHandler }

  override fun getDropFrameHandler(): XDropFrameHandler? = getIfOnlyOneExists { it.dropFrameHandler }

  override fun getAlternativeSourceHandler(): XAlternativeSourceHandler? = getIfOnlyOneExists { it.alternativeSourceHandler }

  // not called from debug session
  override fun stop() {
    super.stop()
  }

  override fun stopAsync(): Promise<in Any> {
    extension.stop()
    return high.stopAsync().thenAsync { low.stopAsync() }
  }

  // not called from debug session
  override fun resume() {
    super.resume()
  }

  override fun resume(context: XSuspendContext?) {
    if (!session.isMixedModeHighProcessReady()) {
      low.resume(context)
      return
    }

    extension.resume()
  }

  // not called from debug session
  override fun runToPosition(position: XSourcePosition) {
    super.runToPosition(position)
  }

  override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) {
    if (!session.isMixedModeHighProcessReady()) {
      low.runToPosition(position, context)
      return
    }

    extension.runToPosition(position, context as XMixedModeSuspendContext)
  }

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
    if ((low as XMixedModeLowLevelDebugProcess).belongsToMe(suspendContext))
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
}