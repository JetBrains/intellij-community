// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diff.impl.DiffUtil
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.InterLineBreakpointProperties
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.util.ThreeState
import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement
import com.intellij.xdebugger.impl.XEditorSourcePosition
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.actions.ToggleLineBreakpointAction
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUIUtil
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Point
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
class XToggleLineBreakpointActionHandler @JvmOverloads constructor(
  private val myTemporary: Boolean,
  private val defaultVerticalPlacement: XLineBreakpointVerticalPlacement = XLineBreakpointVerticalPlacement.ON_LINE,
) : DebuggerActionHandler() {
  override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
    val editor = event.getData(CommonDataKeys.EDITOR)
    if (editor == null || DiffUtil.isDiffEditor(editor)) {
      return false
    }
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    val breakpointPositions = ToggleLineBreakpointAction.getAllPositionsForBreakpoints(project, event.dataContext)
    return breakpointPositions.any { isPlacementAvailable(project, breakpointManager, it, defaultVerticalPlacement) }
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
    val canCreateInterLineBreakpointFromGutter = EditorUtil.isBreakPointsOnLineNumbers()
    val isShiftClickForInterLine = isShiftClick && canCreateInterLineBreakpointFromGutter
    val canRemove = !isFromGutterClick || (!isShiftClickForInterLine && !`is`("debugger.click.disable.breakpoints"))
    val requestedPlacement = getRequestedPlacement(event)
    val isMouseClick = inputEvent is MouseEvent
    val explicitLoggingRequested = isFromGutterClick && editor != null && isMouseClick && !isAltClick && isShiftClick
    val interLineLoggingRequested = editor != null &&
                                    (isInterLineAction(inputEvent, requestedPlacement) ||
                                     isInterLineMouseClick(inputEvent, canCreateInterLineBreakpointFromGutter, event))
    val logExpression = (event.dataContext as? UserDataHolder)?.getUserData(XLineBreakpointManager.LOG_EXPRESSION)
    val balloonPointX = (inputEvent as? MouseEvent)?.getPoint()?.x
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)

    // do not toggle more than once on the same line
    val processedLines = hashSetOf<Int>()
    val futures = mutableListOf<CompletableFuture<*>>()
    for (position in ToggleLineBreakpointAction.getAllPositionsForBreakpoints(project, event.dataContext)) {
      if (processedLines.add(position.getLine())) {
        val mode = getLineBreakpointToggleMode(
          requestedPlacement = requestedPlacement,
          allowOnLineFallback = isFromGutterClick && isMouseClick,
          explicitLoggingRequested = explicitLoggingRequested,
          interLineLoggingRequested = interLineLoggingRequested,
          canShowPopup = isMouseClick,
          isInterLinePlacementAvailable = {
            isPlacementAvailable(project, breakpointManager, position, XLineBreakpointVerticalPlacement.INTER_LINE)
          },
        )
        val selectedText = editor?.getSelectionModel()?.selectedText.takeIf { mode.isLogging }
        val future = XBreakpointUIUtil.toggleLineBreakpointAsync(
          project, position, !isFromGutterClick, position.editor, isAltClick || myTemporary,
          !isFromGutterClick, canRemove, mode.isLogging, logExpression ?: selectedText, mode.placement
        ).thenAccept { breakpoint ->
          if (breakpoint != null && mode.showPopup && balloonPointX != null && editor != null) {
            runInEdt {
              // edit breakpoint
              val logicalPosition = LogicalPosition(breakpoint.getLine() + 1, 0)
              val point = Point(balloonPointX, editor.logicalPositionToXY(logicalPosition).y)
              DebuggerUIUtil.showXBreakpointEditorBalloon(project, point, (editor as EditorEx).getGutterComponentEx(), false, breakpoint)
            }
          }
        }
        futures.add(future)
      }
    }
    return CompletableFuture.allOf(*futures.toTypedArray())
  }

  private fun isInterLineMouseClick(
    inputEvent: InputEvent?,
    canCreateInterLineBreakpointFromGutter: Boolean,
    event: AnActionEvent,
  ): Boolean =
    (inputEvent is MouseEvent && canCreateInterLineBreakpointFromGutter && event.getData(InterLineBreakpointProperties.KEY)?.isLogging == true)

  private fun isInterLineAction(inputEvent: InputEvent?, placement: XLineBreakpointVerticalPlacement): Boolean =
    inputEvent !is MouseEvent && placement == XLineBreakpointVerticalPlacement.INTER_LINE

  private fun getRequestedPlacement(event: AnActionEvent): XLineBreakpointVerticalPlacement {
    return when (event.getData(XLineBreakpointManager.INTER_LINE_BREAKPOINT_KEY)) {
      true -> XLineBreakpointVerticalPlacement.INTER_LINE
      false -> XLineBreakpointVerticalPlacement.ON_LINE
      null -> defaultVerticalPlacement
    }
  }

  /**
   * Tells if the [placement] is available at the [position], either because a breakpoint type accepts the line,
   * or because a breakpoint with that placement is already there.
   *
   * The hover in `XDebuggerLineChangeHandler` must apply the same rule. If the two rules differ, the gutter icon
   * announces one breakpoint and the click creates another.
   */
  private fun isPlacementAvailable(
    project: Project,
    breakpointManager: XBreakpointManagerProxy,
    position: XEditorSourcePosition,
    placement: XLineBreakpointVerticalPlacement,
  ): Boolean {
    val file = position.getFile()
    val line = position.getLine()
    return breakpointManager.getLineBreakpointTypes().any { breakpointType ->
      (XBreakpointUIUtil.supportsPlacement(breakpointType, placement) &&
       breakpointType.canPutAtFast(position.editor, line, project).isAtLeast(ThreeState.UNSURE)) ||
      breakpointManager.findBreakpointAtLine(breakpointType, file, line, placement) != null
    }
  }
}

@VisibleForTesting
internal data class LineBreakpointToggleMode(
  val placement: XLineBreakpointVerticalPlacement,
  val isLogging: Boolean,
  val showPopup: Boolean,
)

/**
 * @param isInterLinePlacementAvailable evaluated only when the fallback can apply, because it scans every line
 *   breakpoint type and analyses the PSI of the line
 */
@VisibleForTesting
internal fun getLineBreakpointToggleMode(
  requestedPlacement: XLineBreakpointVerticalPlacement,
  allowOnLineFallback: Boolean,
  explicitLoggingRequested: Boolean,
  interLineLoggingRequested: Boolean,
  canShowPopup: Boolean,
  isInterLinePlacementAvailable: () -> Boolean,
): LineBreakpointToggleMode {
  val placement = getActualPlacement(requestedPlacement, allowOnLineFallback, isInterLinePlacementAvailable)
  val isInterLineLogging = placement == XLineBreakpointVerticalPlacement.INTER_LINE && interLineLoggingRequested
  val isLogging = explicitLoggingRequested || isInterLineLogging
  val showPopup = canShowPopup && isLogging && !isInterLineLogging
  return LineBreakpointToggleMode(placement, isLogging, showPopup)
}

private fun getActualPlacement(
  requestedPlacement: XLineBreakpointVerticalPlacement,
  allowOnLineFallback: Boolean,
  isInterLinePlacementAvailable: () -> Boolean,
): XLineBreakpointVerticalPlacement {
  return if (allowOnLineFallback &&
             requestedPlacement == XLineBreakpointVerticalPlacement.INTER_LINE &&
             !isInterLinePlacementAvailable()) {
    XLineBreakpointVerticalPlacement.ON_LINE
  }
  else {
    requestedPlacement
  }
}
