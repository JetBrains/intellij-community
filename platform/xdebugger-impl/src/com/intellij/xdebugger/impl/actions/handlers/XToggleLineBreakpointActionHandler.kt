// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diff.impl.DiffUtil
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.util.ThreeState
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.actions.ToggleLineBreakpointAction
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture

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
        if (breakpointType.canPutAtFast(position.editor, line, project).isAtLeast(ThreeState.UNSURE) ||
            breakpointManager.findBreakpointAtLine(breakpointType, file, line) != null) {
          return true
        }
      }
    }
    return false
  }

  override fun perform(project: Project, event: AnActionEvent) {
    toggleLineBreakpoint(project, event)
  }

  @VisibleForTesting
  fun toggleLineBreakpoint(project: Project, event: AnActionEvent): CompletableFuture<Void> {
    val editor = event.getData(CommonDataKeys.EDITOR)
    val isFromGutterClick = event.getData(XLineBreakpointManager.BREAKPOINT_LINE_KEY) != null
    val inputEvent = event.inputEvent
    val isAltClick = isFromGutterClick && inputEvent != null && inputEvent.isAltDown
    val isShiftClick = isFromGutterClick && inputEvent != null && inputEvent.isShiftDown
    val canRemove = !isFromGutterClick || (!isShiftClick && !`is`("debugger.click.disable.breakpoints"))
    val isConditionalBreakpoint = isFromGutterClick && editor != null && inputEvent is MouseEvent
                                  && !isAltClick && isShiftClick
    val selection = if (isConditionalBreakpoint) editor.getSelectionModel().selectedText else null


    // do not toggle more than once on the same line
    val processedLines = hashSetOf<Int>()
    val futures = mutableListOf<CompletableFuture<*>>()
    for (position in ToggleLineBreakpointAction.getAllPositionsForBreakpoints(project, event.dataContext)) {
      if (processedLines.add(position.getLine())) {
        val future = XBreakpointUtil.toggleLineBreakpointProxy(
          project, position, !isFromGutterClick, position.editor, isAltClick || myTemporary,
          !isFromGutterClick, canRemove, isConditionalBreakpoint, selection
        ).thenAccept { breakpoint ->
          if (breakpoint != null && isConditionalBreakpoint) {
            runInEdt {
              // edit breakpoint
              val position = LogicalPosition(breakpoint.getLine() + 1, 0)
              val point = Point(inputEvent.getPoint().x, editor.logicalPositionToXY(position).y)
              DebuggerUIUtil.showXBreakpointEditorBalloon(project, point, (editor as EditorEx).getGutterComponentEx(), false, breakpoint)
            }
          }
        }
        futures.add(future)
      }
    }
    return CompletableFuture.allOf(*futures.toTypedArray())
  }
}
