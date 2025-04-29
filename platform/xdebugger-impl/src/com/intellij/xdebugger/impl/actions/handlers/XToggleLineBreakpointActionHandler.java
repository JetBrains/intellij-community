// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.ToggleLineBreakpointAction;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointTypeProxy;
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
    XBreakpointManagerProxy breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project);
    List<XLineBreakpointTypeProxy> breakpointTypes = breakpointManager.getLineBreakpointTypes();
    Collection<XSourcePosition> breakpointPositions = ToggleLineBreakpointAction.getAllPositionsForBreakpoints(project, event.getDataContext());
    for (XSourcePosition position : breakpointPositions) {
      for (XLineBreakpointTypeProxy breakpointType : breakpointTypes) {
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
    boolean canRemove = !isFromGutterClick || (!isShiftClick && !Registry.is("debugger.click.disable.breakpoints"));

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
                                             canRemove)
          .onSuccess(breakpoint -> {
            if (!isFromGutterClick) return;
            setupLogBreakpoint(breakpoint, inputEvent, editor, project);
          });
      }
    }
  }

  private static void setupLogBreakpoint(final @Nullable XLineBreakpoint<?> breakpoint,
                                         final @Nullable InputEvent inputEvent,
                                         final @Nullable Editor editor,
                                         final @NotNull Project project) {
    if (breakpoint == null || editor == null ||
        !(inputEvent instanceof MouseEvent mouseEvent) ||
        inputEvent.isAltDown() || !inputEvent.isShiftDown()) {
      return;
    }

    // TODO IJPL-185322 move to Backend
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
