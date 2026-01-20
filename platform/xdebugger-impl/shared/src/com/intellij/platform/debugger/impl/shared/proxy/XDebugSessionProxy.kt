// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared.proxy

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XDropFrameHandler
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XSourceKind
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.intellij.xdebugger.ui.IXDebuggerSessionTab
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.event.HyperlinkListener

@ApiStatus.Internal
interface XDebugSessionProxy {
  val runContentDescriptorId: RunContentDescriptorIdImpl?

  val project: Project

  val id: XDebugSessionId

  @get:NlsSafe
  val sessionName: String
  val sessionData: XDebugSessionData
  val consoleView: ConsoleView?
  val restartActions: List<AnAction>
  val extraActions: List<AnAction>
  val extraStopActions: List<AnAction>
  val consoleActions: List<AnAction>
  val processHandler: ProcessHandler?
  val coroutineScope: CoroutineScope
  val editorsProvider: XDebuggerEditorsProvider
  val valueMarkers: XValueMarkers<*, *>?
  val sessionTab: IXDebuggerSessionTab?
  val isStopped: Boolean
  val isPaused: Boolean
  val isSuspended: Boolean
  val isReadOnly: Boolean
  val isPauseActionSupported: Boolean
  val isStepOverActionAllowed: Boolean
  val isStepOutActionAllowed: Boolean
  val isRunToCursorActionAllowed: Boolean
  val isLibraryFrameFilterSupported: Boolean
  val isValuesCustomSorted: Boolean

  @get:NlsSafe
  val currentStateMessage: String
  val currentStateHyperlinkListener: HyperlinkListener?
  val currentEvaluator: XDebuggerEvaluator?
  val smartStepIntoHandlerEntry: XSmartStepIntoHandlerEntry?
  val currentSuspendContextCoroutineScope: CoroutineScope?

  val activeNonLineBreakpointFlow: Flow<XBreakpointProxy?>

  fun getCurrentPosition(): XSourcePosition?
  fun getTopFramePosition(): XSourcePosition?
  fun getFrameSourcePosition(frame: XStackFrame): XSourcePosition?
  fun getFrameSourcePosition(frame: XStackFrame, sourceKind: XSourceKind): XSourcePosition?
  val alternativeSourceKindState: StateFlow<Boolean>
  val currentSourceKind: XSourceKind get() = if (alternativeSourceKindState.value) XSourceKind.ALTERNATIVE else XSourceKind.MAIN

  fun getCurrentExecutionStack(): XExecutionStack?
  fun getCurrentStackFrame(): XStackFrame?
  fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean = executionStack.topFrame == frame)
  fun isTopFrameSelected(): Boolean
  fun hasSuspendContext(): Boolean
  fun isSteppingSuspendContext(): Boolean

  /**
   * Computes execution stacks corresponding to all the live threads in the debug process and adds them to the provided container
   * if suspendContext is available.
   */
  fun computeExecutionStacks(container: XSuspendContext.XExecutionStackContainer)

  /**
   * Computes execution stacks corresponding to all the live threads in the debug process and adds them to the provided container.
   * Uses [com.intellij.xdebugger.XDebugProcess.computeRunningExecutionStacks] on the backend and doesn't require suspendContext.
   */
  fun computeRunningExecutionStacks(container: XSuspendContext.XExecutionStackContainer)
  fun createTabLayouter(): XDebugTabLayouter
  fun addSessionListener(listener: XDebugSessionListener)
  fun addSessionListener(listener: XDebugSessionListener, disposable: Disposable)
  fun rebuildViews()
  fun registerAdditionalActions(leftToolbar: DefaultActionGroup, topLeftToolbar: DefaultActionGroup, settings: DefaultActionGroup)
  fun putKey(sink: DataSink)
  fun createFileColorsCache(onAllComputed: () -> Unit): XStackFramesListColorsCache

  fun areBreakpointsMuted(): Boolean
  fun muteBreakpoints(value: Boolean)
  fun isInactiveSlaveBreakpoint(breakpoint: XBreakpointProxy): Boolean
  fun getDropFrameHandler(): XDropFrameHandler?
  fun getActiveNonLineBreakpoint(): XBreakpointProxy?

  suspend fun stepOver(ignoreBreakpoints: Boolean)
  suspend fun stepOut()
  suspend fun stepInto(ignoreBreakpoints: Boolean)
  suspend fun runToPosition(position: XSourcePosition, ignoreBreakpoints: Boolean)
  suspend fun resume()
  suspend fun pause()

  companion object {
    @JvmField
    val DEBUG_SESSION_PROXY_KEY: DataKey<XDebugSessionProxy> = DataKey.Companion.create("XDebugSessionProxy")
  }

}

@ApiStatus.Internal
interface XSmartStepIntoHandlerEntry {
  val popupTitle: String
}

@ApiStatus.Internal
fun interface XStackFramesListColorsCache {

  @RequiresEdt
  fun get(stackFrame: XStackFrame, project: Project): Color?
}
