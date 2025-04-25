// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironmentProxy
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XDropFrameHandler
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XSourceKind
import com.intellij.xdebugger.impl.XSteppingSuspendContext
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.event.HyperlinkListener

@ApiStatus.Internal
interface XDebugSessionProxy {
  val project: Project

  val id: XDebugSessionId

  @get:NlsSafe
  val sessionName: String
  val sessionData: XDebugSessionData
  val consoleView: ConsoleView?
  val restartActions: List<AnAction>
  val extraActions: List<AnAction>
  val extraStopActions: List<AnAction>
  val processHandler: ProcessHandler?
  val coroutineScope: CoroutineScope
  val editorsProvider: XDebuggerEditorsProvider
  val valueMarkers: XValueMarkers<*, *>?
  val sessionTab: XDebugSessionTab?
  val isStopped: Boolean
  val isPaused: Boolean
  val isSuspended: Boolean
  val isReadOnly: Boolean
  val isPauseActionSupported: Boolean
  val isLibraryFrameFilterSupported: Boolean

  val environmentProxy: ExecutionEnvironmentProxy?

  @get:NlsSafe
  val currentStateMessage: String
  val currentStateHyperlinkListener: HyperlinkListener?
  val currentEvaluator: XDebuggerEvaluator?
  val smartStepIntoHandlerEntry: XSmartStepIntoHandlerEntry?
  val currentSuspendContextCoroutineScope: CoroutineScope?

  fun getCurrentPosition(): XSourcePosition?
  fun getTopFramePosition(): XSourcePosition?
  fun getFrameSourcePosition(frame: XStackFrame): XSourcePosition?
  fun getFrameSourcePosition(frame: XStackFrame, sourceKind: XSourceKind): XSourcePosition?
  fun getCurrentExecutionStack(): XExecutionStack?
  fun getCurrentStackFrame(): XStackFrame?
  fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean = executionStack.topFrame == frame)
  fun hasSuspendContext(): Boolean
  fun isSteppingSuspendContext(): Boolean
  fun computeExecutionStacks(provideContainer: () -> XSuspendContext.XExecutionStackContainer)
  fun createTabLayouter(): XDebugTabLayouter
  fun addSessionListener(listener: XDebugSessionListener, disposable: Disposable)
  fun rebuildViews()
  fun registerAdditionalActions(leftToolbar: DefaultActionGroup, topLeftToolbar: DefaultActionGroup, settings: DefaultActionGroup)
  fun putKey(sink: DataSink)
  fun updateExecutionPosition()
  fun onTabInitialized(tab: XDebugSessionTab)
  fun createFileColorsCache(framesList: XDebuggerFramesList): XStackFramesListColorsCache

  fun areBreakpointsMuted(): Boolean
  fun muteBreakpoints(value: Boolean)
  fun isInactiveSlaveBreakpoint(breakpoint: XBreakpointProxy): Boolean
  fun getDropFrameHandler(): XDropFrameHandler?

  companion object {
    @JvmField
    val DEBUG_SESSION_PROXY_KEY: DataKey<XDebugSessionProxy> = DataKey.create("XDebugSessionProxy")

    @JvmStatic
    fun useFeProxy(): Boolean = Registry.`is`("xdebugger.toolwindow.split")

    fun showFeWarnings(): Boolean = Registry.`is`("xdebugger.toolwindow.split.warnings")
  }

  // TODO WeakReference<XDebugSession>?
  class Monolith(val session: XDebugSession) : XDebugSessionProxy {
    override val project: Project
      get() = session.project
    override val id: XDebugSessionId
      get() = (session as XDebugSessionImpl).id
    override val sessionName: String
      get() = session.sessionName
    override val sessionData: XDebugSessionData
      get() = (session as XDebugSessionImpl).sessionData
    override val consoleView: ConsoleView?
      get() = session.consoleView
    override val restartActions: List<AnAction>
      get() = (session as? XDebugSessionImpl)?.restartActions ?: emptyList()
    override val extraActions: List<AnAction>
      get() = (session as? XDebugSessionImpl)?.extraActions ?: emptyList()
    override val extraStopActions: List<AnAction>
      get() = (session as? XDebugSessionImpl)?.extraStopActions ?: emptyList()
    override val processHandler: ProcessHandler
      get() = session.debugProcess.processHandler
    override val coroutineScope: CoroutineScope
      get() = (session as XDebugSessionImpl).coroutineScope
    override val editorsProvider: XDebuggerEditorsProvider
      get() = session.debugProcess.editorsProvider
    override val valueMarkers: XValueMarkers<*, *>?
      get() = (session as XDebugSessionImpl).valueMarkers
    override val sessionTab: XDebugSessionTab?
      get() = (session as? XDebugSessionImpl)?.sessionTab
    override val isPaused: Boolean
      get() = session.isPaused
    override val environmentProxy: ExecutionEnvironmentProxy?
      get() = null // Monolith shouldn't provide proxy, since the real one ExecutionEnvironment will be used
    override val isStopped: Boolean
      get() = session.isStopped
    override val isReadOnly: Boolean
      get() = (session as? XDebugSessionImpl)?.isReadOnly ?: false
    override val isSuspended: Boolean
      get() = session.isSuspended
    override val isPauseActionSupported: Boolean
      get() = (session as? XDebugSessionImpl)?.isPauseActionSupported() ?: false
    override val isLibraryFrameFilterSupported: Boolean
      get() = session.debugProcess.isLibraryFrameFilterSupported

    override val currentStateHyperlinkListener: HyperlinkListener?
      get() = session.debugProcess.currentStateHyperlinkListener

    override val currentStateMessage: String
      get() = session.debugProcess.currentStateMessage

    override val currentEvaluator: XDebuggerEvaluator?
      get() = session.debugProcess.evaluator

    override val smartStepIntoHandlerEntry: XSmartStepIntoHandlerEntry? by lazy {
      val handler = session.debugProcess.smartStepIntoHandler ?: return@lazy null
      object : XSmartStepIntoHandlerEntry {
        override val popupTitle: String get() = handler.popupTitle
      }
    }

    override val currentSuspendContextCoroutineScope: CoroutineScope?
      get() = (session as XDebugSessionImpl).currentSuspendCoroutineScope

    override fun getCurrentPosition(): XSourcePosition? {
      return session.currentPosition
    }

    override fun getTopFramePosition(): XSourcePosition? {
      return session.topFramePosition
    }

    override fun getFrameSourcePosition(frame: XStackFrame): XSourcePosition? {
      return (session as? XDebugSessionImpl)?.getFrameSourcePosition(frame)
    }

    override fun getFrameSourcePosition(frame: XStackFrame, sourceKind: XSourceKind): XSourcePosition? {
      return (session as? XDebugSessionImpl)?.getFrameSourcePosition(frame, sourceKind)
    }

    override fun getCurrentExecutionStack(): XExecutionStack? {
      return (session as? XDebugSessionImpl)?.currentExecutionStack
    }

    override fun getCurrentStackFrame(): XStackFrame? {
      return session.currentStackFrame
    }

    override fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean) {
      (session as? XDebugSessionImpl)?.setCurrentStackFrame(executionStack, frame, isTopFrame)
    }

    override fun hasSuspendContext(): Boolean {
      return session.suspendContext != null
    }

    override fun isSteppingSuspendContext(): Boolean {
      return session.suspendContext is XSteppingSuspendContext
    }

    override fun computeExecutionStacks(provideContainer: () -> XSuspendContext.XExecutionStackContainer) {
      session.suspendContext?.computeExecutionStacks(provideContainer())
    }

    override fun createTabLayouter(): XDebugTabLayouter = session.debugProcess.createTabLayouter()

    override fun addSessionListener(listener: XDebugSessionListener, disposable: Disposable) {
      session.addSessionListener(listener, disposable)
    }

    override fun rebuildViews() {
      session.rebuildViews()
    }

    override fun registerAdditionalActions(leftToolbar: DefaultActionGroup, topLeftToolbar: DefaultActionGroup, settings: DefaultActionGroup) {
      session.debugProcess.registerAdditionalActions(leftToolbar, topLeftToolbar, settings)
    }

    override fun putKey(sink: DataSink) {
      sink[XDebugSession.DATA_KEY] = session
    }

    override fun updateExecutionPosition() {
      (session as? XDebugSessionImpl)?.updateExecutionPosition()
    }

    override fun onTabInitialized(tab: XDebugSessionTab) {
      (session as? XDebugSessionImpl)?.tabInitialized(tab)
    }

    override fun createFileColorsCache(framesList: XDebuggerFramesList): XStackFramesListColorsCache {
      return XStackFramesListColorsCache.Monolith(session as XDebugSessionImpl, framesList)
    }

    override fun areBreakpointsMuted(): Boolean {
      return session.areBreakpointsMuted()
    }

    override fun muteBreakpoints(value: Boolean) {
      session.setBreakpointMuted(value)
    }

    override fun isInactiveSlaveBreakpoint(breakpoint: XBreakpointProxy): Boolean {
      if (breakpoint !is XBreakpointProxy.Monolith) {
        return false
      }
      return (session as XDebugSessionImpl).isInactiveSlaveBreakpoint(breakpoint.breakpoint)
    }

    override fun getDropFrameHandler(): XDropFrameHandler? {
      return session.debugProcess.dropFrameHandler
    }
  }
}

@ApiStatus.Internal
interface XSmartStepIntoHandlerEntry {
  val popupTitle: String
}
