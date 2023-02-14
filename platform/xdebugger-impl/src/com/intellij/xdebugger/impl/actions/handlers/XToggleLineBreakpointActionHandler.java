// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diff.impl.DiffUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class XToggleLineBreakpointActionHandler extends DebuggerActionHandler {

  private final boolean myTemporary;

  public XToggleLineBreakpointActionHandler(boolean temporary) {
    myTemporary = temporary;
  }

  @Override
  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    if (editor == null || DiffUtil.isDiffEditor(editor)) {
      return false;
    }
    XLineBreakpointType<?>[] breakpointTypes = XDebuggerUtil.getInstance().getLineBreakpointTypes();
    final XBreakpointManager breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
    for (XSourcePosition position : XDebuggerUtilImpl.getAllCaretsPositions(project, event.getDataContext())) {
      for (XLineBreakpointType<?> breakpointType : breakpointTypes) {
        final VirtualFile file = position.getFile();
        final int line = position.getLine();
        if (breakpointType.canPutAt(file, line, project) || breakpointManager.findBreakpointAtLine(breakpointType, file, line) != null) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void perform(@NotNull final Project project, final AnActionEvent event) {
    Editor editor = event.getData(CommonDataKeys.EDITOR);
    // do not toggle more than once on the same line
    Set<Integer> processedLines = new HashSet<>();
    for (XSourcePosition position : XDebuggerUtilImpl.getAllCaretsPositions(project, event.getDataContext())) {
      if (processedLines.add(position.getLine())) {
        XBreakpointUtil.toggleLineBreakpoint(project, position, editor, myTemporary, true, true);
      }
    }
  }
}
