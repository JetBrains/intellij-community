// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diff.impl.DiffUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.actions.ToggleLineBreakpointAction
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil.toggleLineBreakpoint
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

@ApiStatus.Internal
class XToggleLineBreakpointActionHandler(private val myTemporary: Boolean) : DebuggerActionHandler() {
  override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
    val editor = event.getData(CommonDataKeys.EDITOR)
    if (editor == null || DiffUtil.isDiffEditor(editor)) {
      return false
    }
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    val breakpointTypes = breakpointManager.getLineBreakpointTypes()
    val breakpointPositions = ToggleLineBreakpointAction.getAllPositionsForBreakpoints(project, event.dataContext)
    for (position in breakpointPositions) {
      for (breakpointType in breakpointTypes) {
        val file = position.getFile()
        val line = position.getLine()
        if (breakpointType.canPutAt(file, line, project) || breakpointManager.findBreakpointAtLine(breakpointType, file, line) != null) {
          return true
        }
      }
    }
    return false
  }

  override fun perform(project: Project, event: AnActionEvent) {
    val editor = event.getData(CommonDataKeys.EDITOR)
    val isFromGutterClick = event.getData(XLineBreakpointManager.BREAKPOINT_LINE_KEY) != null
    val inputEvent = event.inputEvent
    val isAltClick = isFromGutterClick && inputEvent != null && inputEvent.isAltDown
    val isShiftClick = isFromGutterClick && inputEvent != null && inputEvent.isShiftDown
    val canRemove = !isFromGutterClick || (!isShiftClick && !`is`("debugger.click.disable.breakpoints"))

    // do not toggle more than once on the same line
    val processedLines = hashSetOf<Int>()
    for (position in ToggleLineBreakpointAction.getAllPositionsForBreakpoints(project, event.dataContext)) {
      if (processedLines.add(position.getLine())) {
        toggleLineBreakpoint(project,
                             position,
                             !isFromGutterClick,
                             editor,
                             isAltClick || myTemporary,
                             !isFromGutterClick,
                             canRemove)
          .onSuccess { breakpoint ->
            if (!isFromGutterClick) return@onSuccess
            setupLogBreakpoint(breakpoint, inputEvent, editor, project)
          }
      }
    }
  }

  companion object {
    private fun setupLogBreakpoint(
      breakpoint: XLineBreakpoint<*>?,
      inputEvent: InputEvent?,
      editor: Editor?,
      project: Project,
    ) {
      if (breakpoint == null || editor == null || (inputEvent !is MouseEvent) ||
          inputEvent.isAltDown || !inputEvent.isShiftDown
      ) {
        return
      }

      // TODO IJPL-185322 move to Backend
      breakpoint.setSuspendPolicy(SuspendPolicy.NONE)
      val selection = editor.getSelectionModel().selectedText
      if (selection != null) {
        breakpoint.setLogExpression(selection)
      }
      else {
        breakpoint.setLogMessage(true)
      }
      // edit breakpoint
      val position = LogicalPosition(breakpoint.getLine() + 1, 0)
      val point = Point(inputEvent.getPoint().x, editor.logicalPositionToXY(position).y)
      DebuggerUIUtil.showXBreakpointEditorBalloon(project, point, (editor as EditorEx).getGutterComponentEx(), false, breakpoint)
    }
  }
}
