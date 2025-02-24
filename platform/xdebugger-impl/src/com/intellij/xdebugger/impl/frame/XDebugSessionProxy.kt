// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XSteppingSuspendContext
import com.intellij.xdebugger.impl.ui.XDebugSessionData
import com.intellij.xdebugger.ui.XDebugTabLayouter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XDebugSessionProxy {
  val project: Project

  @get:NlsSafe
  val sessionName: String
  val sessionData: XDebugSessionData?
  val consoleView: ConsoleView?
  val restartActions: List<AnAction>
  val extraActions: List<AnAction>
  val extraStopActions: List<AnAction>
  val processHandler: ProcessHandler

  fun getFrameSourcePosition(frame: XStackFrame): XSourcePosition?
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

  companion object {
    @JvmField
    val DEBUG_SESSION_PROXY_KEY: DataKey<XDebugSessionProxy> = DataKey.create("XDebugSessionProxy")
  }

  // TODO WeakReference<XDebugSession>?
  class Monolith(val session: XDebugSession) : XDebugSessionProxy {
    override val project: Project
      get() = session.project
    override val sessionName: String
      get() = session.sessionName
    override val sessionData: XDebugSessionData?
      get() = (session as? XDebugSessionImpl)?.sessionData
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

    override fun getFrameSourcePosition(frame: XStackFrame): XSourcePosition? {
      return (session as? XDebugSessionImpl)?.getFrameSourcePosition(frame)
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
  }
}