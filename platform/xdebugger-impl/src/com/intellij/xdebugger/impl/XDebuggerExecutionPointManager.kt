// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter


internal class XDebuggerExecutionPointManager(project: Project,
                                              messageBusConnection: MessageBusConnection) {
  private val mainExecutionPointHighlighter: ExecutionPointHighlighter = ExecutionPointHighlighter(project, messageBusConnection)
  private val alternativeExecutionPointHighlighter: ExecutionPointHighlighter = ExecutionPointHighlighter(project, messageBusConnection)

  init {
    messageBusConnection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
      override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
        if (currentSession == null) {
          mainExecutionPointHighlighter.hide()
          alternativeExecutionPointHighlighter.hide()
        }
      }
    })
  }

  private fun getExecutionPointHighlighter(sourceKind: XSourceKind): ExecutionPointHighlighter {
    return when (sourceKind) {
      XSourceKind.MAIN -> mainExecutionPointHighlighter
      XSourceKind.ALTERNATIVE -> alternativeExecutionPointHighlighter
    }
  }

  fun updateExecutionPoint(sourceKind: XSourceKind,
                           position: XSourcePosition?,
                           gutterIconRenderer: GutterIconRenderer?,
                           isTopFrame: Boolean,
                           isActiveSourceKind: Boolean) {
    val executionPointHighlighter = getExecutionPointHighlighter(sourceKind)
    if (position != null) {
      executionPointHighlighter.show(position, !isTopFrame, gutterIconRenderer);
    }
    else {
      executionPointHighlighter.hide()
    }
  }

  fun updateRenderer(renderer: GutterIconRenderer?) {
    mainExecutionPointHighlighter.updateGutterIcon(renderer)
    alternativeExecutionPointHighlighter.updateGutterIcon(renderer)
  }

  fun showExecutionPosition(sourceKind: XSourceKind) {
    getExecutionPointHighlighter(sourceKind).navigateTo()
  }

  fun isFullLineHighlighter(): Boolean {
    return mainExecutionPointHighlighter.isFullLineHighlighter
  }
}
