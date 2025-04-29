// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.actions.handlers.XToggleLineBreakpointActionHandler;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointManager;
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointTypeProxy;
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class ToggleLineBreakpointAction extends XDebuggerActionBase implements DumbAware, Toggleable {
  private static final XToggleLineBreakpointActionHandler ourHandler = new XToggleLineBreakpointActionHandler(false);

  public ToggleLineBreakpointAction() {
    super(true);
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(final @NotNull DebuggerSupport debuggerSupport) {
    return ourHandler;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);

    if (ActionPlaces.TOUCHBAR_GENERAL.equals(event.getPlace())) {
      event.getPresentation().setIcon(AllIcons.Debugger.Db_set_breakpoint);
    }

    final boolean selected = hasLineBreakpoint(event);
    Toggleable.setSelected(event.getPresentation(), selected);
  }

  private static boolean hasLineBreakpoint(@NotNull AnActionEvent e) {
    final Project proj = e.getProject();
    if (proj == null) {
      return false;
    }

    XBreakpointManagerProxy breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(proj);
    List<XLineBreakpointTypeProxy> breakpointTypes = breakpointManager.getLineBreakpointTypes();
    for (XSourcePosition position : getAllPositionsForBreakpoints(proj, e.getDataContext())) {
      for (XLineBreakpointTypeProxy breakpointType : breakpointTypes) {
        final VirtualFile file = position.getFile();
        final int line = position.getLine();
        if (breakpointManager.findBreakpointAtLine(breakpointType, file, line) != null) {
          return true;
        }
      }
    }
    return false;
  }

  public static @NotNull Collection<XSourcePosition> getAllPositionsForBreakpoints(@NotNull Project project, DataContext context) {
    Editor editor = XDebuggerUtilImpl.getEditor(project, context);
    if (editor == null) {
      return Collections.emptyList();
    }

    VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    List<XSourcePosition> res = new SmartList<>();

    Integer line = XLineBreakpointManager.BREAKPOINT_LINE_KEY.getData(context);
    if (line != null) {
      XSourcePositionImpl position = XSourcePositionImpl.create(file, line);
      res.add(position);
      return res;
    }

    for (Caret caret : editor.getCaretModel().getAllCarets()) {
      XSourcePositionImpl position = XSourcePositionImpl.createByOffset(file, caret.getOffset());
      if (position != null) {
        res.add(position);
      }
    }
    return res;
  }
}
