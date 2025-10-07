// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.util.asDisposable
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.util.MonolithUtils
import kotlinx.coroutines.launch


private class ExecutionPointManagerChangeListener(val project: Project) : FrontendXDebuggerManagerListener {
  init {
    val breakpointsManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)

    breakpointsManager.subscribeOnBreakpointsChanges(project) {
      updateGutterRendererIcon(project)
    }
  }

  override fun sessionStarted(session: XDebugSessionProxy) {
    session.coroutineScope.launch {
      session.activeNonLineBreakpointFlow.collect {
        updateGutterRendererIcon(project)
      }
    }

    session.addSessionListener(object : XDebugSessionListener {
      override fun stackFrameChanged() {
        updateExecutionPosition(project)
      }

      override fun sessionPaused() {
        updateExecutionPosition(project)
      }

      override fun sessionStopped() {
        updateExecutionPosition(project)
      }

    }, session.coroutineScope.asDisposable())
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
    val xDebugSession = MonolithUtils.findSessionById(currentSession.id)
    if (xDebugSession != null) {
      executionPointManager.alternativeSourceKindFlow = xDebugSession.alternativeSourceKindState
      updateExecutionPosition(project, xDebugSession.currentSourceKind)
    }
    else {
      updateExecutionPosition(project)
    }
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

// TODO: need to support navigationSourceKind properly from the calling side!
internal fun updateExecutionPosition(project: Project, navigationSourceKind: XSourceKind = XSourceKind.MAIN) {
  val session = XDebugManagerProxy.getInstance().getCurrentSessionProxy(project) ?: return

  val isTopFrame = session.isTopFrameSelected()
  val currentStackFrame = session.getCurrentStackFrame()

  val mainSourcePosition = currentStackFrame?.let { session.getFrameSourcePosition(it, XSourceKind.MAIN) }
  val alternativeSourcePosition = currentStackFrame?.let { session.getFrameSourcePosition(it, XSourceKind.ALTERNATIVE) }

  XDebugManagerProxy.getInstance().getDebuggerExecutionPointManager(project)?.setExecutionPoint(mainSourcePosition, alternativeSourcePosition, isTopFrame, navigationSourceKind)
  updateGutterRendererIcon(project)
}

private fun getGutterRenderer(breakpoint: XBreakpointProxy?, session: XDebugSessionProxy): GutterIconRenderer? =
  breakpoint?.createGutterIconRenderer() ?: session.getCurrentExecutionStack()?.executionLineIconRenderer

private fun setGutterRenderer(project: Project, renderer: GutterIconRenderer?) {
  XDebugManagerProxy.getInstance().getDebuggerExecutionPointManager(project)?.gutterIconRenderer = renderer
}
