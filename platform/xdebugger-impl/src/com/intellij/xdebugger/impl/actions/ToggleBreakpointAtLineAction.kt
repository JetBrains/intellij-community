// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import java.awt.Point
import java.awt.event.MouseEvent

class ToggleBreakpointAtLineAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val document = editor.document
    val file = FileDocumentManager.getInstance().getFile(document)
    val mouseEvent = e.inputEvent as? MouseEvent ?: return
    val line = e.getData(XLineBreakpointManager.BREAKPOINT_LINE_KEY) ?: return
    XBreakpointUtil.toggleLineBreakpoint(project,
                                         XSourcePositionImpl.create(file, line),
                                         editor,
                                         mouseEvent.isAltDown,
                                         false,
                                         !mouseEvent.isShiftDown && !Registry.`is`("debugger.click.disable.breakpoints"))
      .onSuccess { breakpoint ->
        if (!mouseEvent.isAltDown && mouseEvent.isShiftDown && breakpoint != null) {
          breakpoint.suspendPolicy = SuspendPolicy.NONE
          val selection = editor.selectionModel.selectedText
          if (selection != null) {
            breakpoint.setLogExpression(selection)
          }
          else {
            breakpoint.setLogMessage(true)
          }
          // edit breakpoint
          val point = Point(mouseEvent.point.x, editor.visualLineToY(breakpoint.line + 1))
          DebuggerUIUtil.showXBreakpointEditorBalloon(project, point, (editor as EditorEx).gutterComponentEx, false, breakpoint)
        }
      }
  }
}
