// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
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
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.breakpoints.asProxy
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
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
  val sessionTab: XDebugSessionTab?
  val sessionTabWhenInitialized: Deferred<XDebugSessionTab>
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
  fun getCurrentExecutionStack(): XExecutionStack?
  fun getCurrentStackFrame(): XStackFrame?
  fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean = executionStack.topFrame == frame)
  fun isTopFrameSelected(): Boolean
  fun hasSuspendContext(): Boolean
  fun isSteppingSuspendContext(): Boolean
  fun computeExecutionStacks(provideContainer: () -> XSuspendContext.XExecutionStackContainer)
  fun createTabLayouter(): XDebugTabLayouter
  fun addSessionListener(listener: XDebugSessionListener, disposable: Disposable)
  fun rebuildViews()
  fun registerAdditionalActions(leftToolbar: DefaultActionGroup, topLeftToolbar: DefaultActionGroup, settings: DefaultActionGroup)
  fun putKey(sink: DataSink)
  fun createFileColorsCache(framesList: XDebuggerFramesList): XStackFramesListColorsCache

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
    val DEBUG_SESSION_PROXY_KEY: DataKey<XDebugSessionProxy> = DataKey.create("XDebugSessionProxy")
  }

  class Monolith internal constructor(val session: XDebugSession) : XDebugSessionProxy {

    val sessionImpl: XDebugSessionImpl get() = session as XDebugSessionImpl
    private val sessionImplIfAvailable get() = session as? XDebugSessionImpl

    override val runContentDescriptorId: RunContentDescriptorIdImpl?
      get() = sessionImplIfAvailable?.getMockRunContentDescriptorIfInitialized()?.id as RunContentDescriptorIdImpl?

    override val project: Project
      get() = session.project
    override val id: XDebugSessionId
      get() = sessionImpl.id
    override val sessionName: String
      get() = session.sessionName
    override val sessionData: XDebugSessionData
      get() = sessionImpl.sessionData
    override val consoleView: ConsoleView?
      get() = session.consoleView
    override val restartActions: List<AnAction>
      get() = sessionImpl.restartActions
    override val extraActions: List<AnAction>
      get() = sessionImpl.extraActions
    override val extraStopActions: List<AnAction>
      get() = sessionImpl.extraStopActions
    override val consoleActions: List<AnAction>
      get() = consoleView?.createConsoleActions()?.toList() ?: emptyList()
    override val processHandler: ProcessHandler
      get() = session.debugProcess.processHandler
    override val coroutineScope: CoroutineScope
      get() = sessionImpl.coroutineScope
    override val editorsProvider: XDebuggerEditorsProvider
      get() = session.debugProcess.editorsProvider
    override val valueMarkers: XValueMarkers<*, *>?
      get() = sessionImplIfAvailable?.valueMarkers
    override val sessionTab: XDebugSessionTab?
      get() = sessionImplIfAvailable?.sessionTab
    override val sessionTabWhenInitialized: Deferred<XDebugSessionTab>
      get() = sessionImpl.sessionTabDeferred
    override val isPaused: Boolean
      get() = session.isPaused
    override val isStopped: Boolean
      get() = session.isStopped
    override val isReadOnly: Boolean
      get() = sessionImpl.isReadOnly
    override val isSuspended: Boolean
      get() = session.isSuspended
    override val isPauseActionSupported: Boolean
      get() = sessionImpl.isPauseActionSupported()
    override val isStepOverActionAllowed: Boolean
      get() = sessionImpl.isStepOverActionAllowed
    override val isStepOutActionAllowed: Boolean
      get() = sessionImpl.isStepOutActionAllowed
    override val isRunToCursorActionAllowed: Boolean
      get() = sessionImpl.isRunToCursorActionAllowed
    override val isLibraryFrameFilterSupported: Boolean
      get() = session.debugProcess.isLibraryFrameFilterSupported
    override val isValuesCustomSorted: Boolean
      get() = session.debugProcess.isValuesCustomSorted

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
      get() = sessionImplIfAvailable?.currentSuspendCoroutineScope

    override val activeNonLineBreakpointFlow: Flow<XBreakpointProxy?>
      get() = sessionImpl
      .activeNonLineBreakpointFlow.map { (it as? XBreakpointBase<*, *, *>)?.asProxy() }

    override fun getCurrentPosition(): XSourcePosition? {
      return session.currentPosition
    }

    override fun getTopFramePosition(): XSourcePosition? {
      return session.topFramePosition
    }

    override fun getFrameSourcePosition(frame: XStackFrame): XSourcePosition? {
      return sessionImplIfAvailable?.getFrameSourcePosition(frame)
    }

    override fun getFrameSourcePosition(frame: XStackFrame, sourceKind: XSourceKind): XSourcePosition? {
      return sessionImplIfAvailable?.getFrameSourcePosition(frame, sourceKind)
    }

    override fun getCurrentExecutionStack(): XExecutionStack? {
      return sessionImplIfAvailable?.currentExecutionStack
    }

    override fun getCurrentStackFrame(): XStackFrame? {
      return session.currentStackFrame
    }

    override fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean) {
      session.setCurrentStackFrame(executionStack, frame, isTopFrame)
    }

    override fun isTopFrameSelected(): Boolean {
      return sessionImpl.isTopFrameSelected
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

    override fun createFileColorsCache(framesList: XDebuggerFramesList): XStackFramesListColorsCache {
      return XStackFramesListColorsCache.Monolith(sessionImpl, framesList)
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
      return sessionImpl.isInactiveSlaveBreakpoint(breakpoint.breakpoint)
    }

    override fun getDropFrameHandler(): XDropFrameHandler? {
      return session.debugProcess.dropFrameHandler
    }

    override fun getActiveNonLineBreakpoint(): XBreakpointProxy? {
      val breakpoint = sessionImplIfAvailable?.activeNonLineBreakpoint ?: return null
      if (breakpoint !is XBreakpointBase<*, *, *>) return null
      return breakpoint.asProxy()
    }

    override suspend fun stepOver(ignoreBreakpoints: Boolean) {
      withContext(Dispatchers.EDT) {
        session.stepOver(ignoreBreakpoints)
      }
    }

    override suspend fun stepOut() {
      withContext(Dispatchers.EDT) {
        session.stepOut()
      }
    }

    override suspend fun stepInto(ignoreBreakpoints: Boolean) {
      withContext(Dispatchers.EDT) {
        if (ignoreBreakpoints) {
          session.forceStepInto()
        }
        else {
          session.stepInto()
        }
      }
    }

    override suspend fun runToPosition(position: XSourcePosition, ignoreBreakpoints: Boolean) {
      withContext(Dispatchers.EDT) {
        session.runToPosition(position, ignoreBreakpoints)
      }
    }

    override suspend fun pause() {
      withContext(Dispatchers.EDT) {
        session.pause()
      }
    }

    override suspend fun resume() {
      withContext(Dispatchers.EDT) {
        session.resume()
      }
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is Monolith) return false

      return session == other.session
    }

    override fun hashCode(): Int {
      return session.hashCode()
    }

  }
}

@ApiStatus.Internal
interface XSmartStepIntoHandlerEntry {
  val popupTitle: String
}
