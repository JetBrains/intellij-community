// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.proxy

import com.intellij.execution.RunContentDescriptorIdImpl
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.debugger.impl.shared.proxy.XSmartStepIntoHandlerEntry
import com.intellij.platform.debugger.impl.shared.proxy.XStackFramesListColorsCache
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
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
import com.intellij.xdebugger.impl.updateExecutionPosition
import com.intellij.xdebugger.impl.XSteppingSuspendContext
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.intellij.xdebugger.impl.ui.XDebugSessionTab
import com.intellij.xdebugger.ui.XDebugTabLayouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.swing.event.HyperlinkListener

internal class MonolithSessionProxy(val session: XDebugSession) : XDebugSessionProxy {

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
    get() = sessionImplIfAvailable?.getSuspendContextModel()?.coroutineScope

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

  override val alternativeSourceKindState: StateFlow<Boolean>
    get() = sessionImpl.alternativeSourceKindState

  override val currentSourceKind: XSourceKind
    get() = sessionImpl.currentSourceKind

  override fun getCurrentExecutionStack(): XExecutionStack? {
    return sessionImplIfAvailable?.currentExecutionStack
  }

  override fun getCurrentStackFrame(): XStackFrame? {
    return session.currentStackFrame
  }

  override fun setCurrentStackFrame(executionStack: XExecutionStack, frame: XStackFrame, isTopFrame: Boolean) {
    session.setCurrentStackFrame(executionStack, frame, isTopFrame)
    if (session.currentStackFrame === frame) {
      updateExecutionPosition(this)
    }
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

  override fun computeExecutionStacks(container: XSuspendContext.XExecutionStackContainer) {
    session.suspendContext?.computeExecutionStacks(container)
  }

  override fun computeRunningExecutionStacks(container: XSuspendContext.XExecutionStackGroupContainer) {
    session.debugProcess.computeRunningExecutionStacks(container, session.suspendContext)
  }

  override fun createTabLayouter(): XDebugTabLayouter = session.debugProcess.createTabLayouter()

  override fun addSessionListener(listener: XDebugSessionListener) {
    session.addSessionListener(listener)
  }

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

  override fun createFileColorsCache(onAllComputed: () -> Unit): XStackFramesListColorsCache {
    return sessionImpl.sessionData.getOrCreateUserData(COLOR_CACHE_KEY) {
      MonolithFramesColorCache(sessionImpl, onAllComputed)
    }
  }

  override fun areBreakpointsMuted(): Boolean {
    return session.areBreakpointsMuted()
  }

  override fun muteBreakpoints(value: Boolean) {
    session.setBreakpointMuted(value)
  }

  override fun isInactiveSlaveBreakpoint(breakpoint: XBreakpointProxy): Boolean {
    if (breakpoint !is MonolithBreakpointProxy) {
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
    if (other !is MonolithSessionProxy) return false

    return session == other.session
  }

  override fun hashCode(): Int {
    return session.hashCode()
  }

  companion object {
    private val COLOR_CACHE_KEY = Key.create<XStackFramesListColorsCache>("COLOR_CACHE_KEY")
  }
}

@Service(Service.Level.PROJECT)
private class XDebugSessionProxyKeeper {
  private val proxyMap = ConcurrentHashMap<XDebugSession, XDebugSessionProxy>()

  fun getOrCreateProxy(session: XDebugSession): XDebugSessionProxy {
    return proxyMap.computeIfAbsent(session, this::createProxy)
  }

  @OptIn(AwaitCancellationAndInvoke::class)
  private fun createProxy(session: XDebugSession): XDebugSessionProxy {
    if (session is XDebugSessionImpl) {
      session.coroutineScope.awaitCancellationAndInvoke {
        proxyMap.remove(session)
      }
    }
    return MonolithSessionProxy(session)
  }
}

internal fun XDebugSession.asProxy(): XDebugSessionProxy =
  project.service<XDebugSessionProxyKeeper>().getOrCreateProxy(this)