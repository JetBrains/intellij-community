// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter
import com.intellij.xdebugger.XDebugSessionListener
import kotlinx.coroutines.launch


private class ExecutionPointManagerChangeListener(val project: Project) : XDebuggerManagerProxyListener {
  private var breakpointChangeListenerInitialized = false

  override fun sessionStarted(session: XDebugSessionProxy) {
    if (!breakpointChangeListenerInitialized) {
      breakpointChangeListenerInitialized = true

      val breakpointsManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)

      breakpointsManager.subscribeOnBreakpointsChanges(project) {
        updateGutterRendererIcon(project)
      }
    }
    session.coroutineScope.launch {
      session.activeNonLineBreakpointFlow.collect {
        updateGutterRendererIcon(project)
      }
    }

    session.addSessionListener(object : XDebugSessionListener {
      override fun stackFrameChanged() {
        updateExecutionPosition(session)
      }

      override fun sessionResumed() {
        updateExecutionPosition(session)
      }

      override fun sessionPaused() {
        updateExecutionPosition(session, checkAlternativePosition = true)
      }

      override fun sessionStopped() {
        updateExecutionPosition(session)
      }

    })
  }

  override fun activeSessionChanged(previousSession: XDebugSessionProxy?, currentSession: XDebugSessionProxy?) {
    updateAfterActiveSessionChanged(currentSession, project)
  }
}

private fun updateAfterActiveSessionChanged(
  currentSession: XDebugSessionProxy?,
  project: Project,
) {
  val executionPointManager = XDebugManagerProxy.getInstance().getDebuggerExecutionPointManager(project) ?: return
  if (currentSession != null) {
    executionPointManager.alternativeSourceKindFlow = currentSession.alternativeSourceKindState
    updateExecutionPosition(currentSession)
  }
  else {
    executionPointManager.clearExecutionPoint()
  }
}

private fun updateGutterRendererIcon(project: Project) {
  val session = XDebugManagerProxy.getInstance().getCurrentSessionProxy(project)

  if (session != null && session.isTopFrameSelected()) {
    val renderer = getGutterRenderer(session.getActiveNonLineBreakpoint(), session)
    setGutterRenderer(project, renderer)
  }
  else {
    setGutterRenderer(project, null)
  }
}

private fun detectSourceKind(session: XDebugSessionProxy): XSourceKind {
  if (session.currentSourceKind == XSourceKind.ALTERNATIVE) return XSourceKind.ALTERNATIVE
  // TODO XAlternativeSourceHandler.isAlternativeSourceKindPreferred is supported only in monolith
  val xDebugSession = XDebuggerEntityConverter.getSession(session) ?: return XSourceKind.MAIN
  val suspendContext = xDebugSession.suspendContext ?: return XSourceKind.MAIN
  val alternativeSourceHandler = xDebugSession.debugProcess.alternativeSourceHandler ?: return XSourceKind.MAIN
  val useAlternative = alternativeSourceHandler.isAlternativeSourceKindPreferred(suspendContext)
  return if (useAlternative) XSourceKind.ALTERNATIVE else XSourceKind.MAIN
}

internal fun updateExecutionPosition(session: XDebugSessionProxy, checkAlternativePosition: Boolean = false) {
  val currentSession = XDebugManagerProxy.getInstance().getCurrentSessionProxy(session.project) ?: return
  if (currentSession.id != session.id) return

  val executionPointManager = XDebugManagerProxy.getInstance().getDebuggerExecutionPointManager(session.project)
  if (executionPointManager != null) {
    val isTopFrame = session.isTopFrameSelected()
    val currentStackFrame = session.getCurrentStackFrame()

    val mainSourcePosition = currentStackFrame?.let { session.getFrameSourcePosition(it, XSourceKind.MAIN) }
    val alternativeSourcePosition = currentStackFrame?.let { session.getFrameSourcePosition(it, XSourceKind.ALTERNATIVE) }

    val navigationSourceKind = if (checkAlternativePosition) detectSourceKind(session) else XSourceKind.MAIN
    executionPointManager.setExecutionPoint(mainSourcePosition, alternativeSourcePosition, isTopFrame, navigationSourceKind)
  }
  updateGutterRendererIcon(session.project)
}

private fun getGutterRenderer(breakpoint: XBreakpointProxy?, session: XDebugSessionProxy): GutterIconRenderer? =
  breakpoint?.createGutterIconRenderer() ?: session.getCurrentExecutionStack()?.executionLineIconRenderer

private fun setGutterRenderer(project: Project, renderer: GutterIconRenderer?) {
  XDebugManagerProxy.getInstance().getDebuggerExecutionPointManager(project)?.gutterIconRenderer = renderer
}
