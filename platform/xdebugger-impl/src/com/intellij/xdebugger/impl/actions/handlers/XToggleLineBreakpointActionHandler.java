// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.ToggleLineBreakpointAction;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public class XToggleLineBreakpointActionHandler extends DebuggerActionHandler {

  private final boolean myTemporary;

  public XToggleLineBreakpointActionHandler(boolean temporary) {
    myTemporary = temporary;
  }

  @Override
  public boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    if (editor == null || DiffUtil.isDiffEditor(editor)) {
      return false;
    }
    XLineBreakpointType<?>[] breakpointTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
    XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XSourcePosition position : ToggleLineBreakpointAction.getAllPositionsForBreakpoints(project, event.getDataContext())) {
      for (XLineBreakpointType<?> breakpointType : breakpointTypes) {
        VirtualFile file = position.getFile();
        int line = position.getLine();
        if (breakpointType.canPutAt(file, line, project) || breakpointManager.findBreakpointAtLine(breakpointType, file, line) != null) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void perform(@NotNull Project project, @NotNull AnActionEvent event) {
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    boolean isFromGutterClick = event.getData(XLineBreakpointManager.BREAKPOINT_LINE_KEY) != null;
    InputEvent inputEvent = event.getInputEvent();
    boolean isAltClick = isFromGutterClick && inputEvent != null && inputEvent.isAltDown();
    boolean isShiftClick = isFromGutterClick && inputEvent != null && inputEvent.isShiftDown();

    // do not toggle more than once on the same line
    Set<Integer> processedLines = new HashSet<>();
    for (XSourcePosition position : ToggleLineBreakpointAction.getAllPositionsForBreakpoints(project, event.getDataContext())) {
      if (processedLines.add(position.getLine())) {
        XBreakpointUtil.toggleLineBreakpoint(project,
                                             position,
                                             !isFromGutterClick,
                                             editor,
                                             isAltClick || myTemporary,
                                             !isFromGutterClick,
                                             !isFromGutterClick || (!isShiftClick && !Registry.is("debugger.click.disable.breakpoints")))
          .onSuccess(breakpoint -> {
            if (!isFromGutterClick) return;
            setupLogBreakpoint(breakpoint, inputEvent, editor, project);
          });
      }
    }
  }

  private static void setupLogBreakpoint(@Nullable final XLineBreakpoint<?> breakpoint,
                                         @Nullable final InputEvent inputEvent,
                                         @Nullable final Editor editor,
                                         @NotNull final Project project) {
    if (breakpoint == null || editor == null ||
        !(inputEvent instanceof MouseEvent mouseEvent) ||
        inputEvent.isAltDown() || !inputEvent.isShiftDown()) {
      return;
    }

    breakpoint.setSuspendPolicy(SuspendPolicy.NONE);
    String selection = editor.getSelectionModel().getSelectedText();
    if (selection != null) {
      breakpoint.setLogExpression(selection);
    }
    else {
      breakpoint.setLogMessage(true);
    }
    // edit breakpoint
    LogicalPosition position = new LogicalPosition(breakpoint.getLine() + 1, 0);
    Point point = new Point(mouseEvent.getPoint().x, editor.logicalPositionToXY(position).y);
    DebuggerUIUtil.showXBreakpointEditorBalloon(project, point, ((EditorEx) editor).getGutterComponentEx(), false, breakpoint);
  }
}
