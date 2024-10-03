// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.EditorGutterComponentEx;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ModalityUiUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class AddLineBreakpointAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return;
    XSourcePosition position = getLineBreakpointPosition(e);
    assert position != null;
    XBreakpointUtil.toggleLineBreakpoint(project, position, false, editor, false, false, true)
      .onSuccess(bp -> {
        if (bp != null && isConditional()) {
          ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(), () -> {
            EditorGutterComponentEx gutter = (EditorGutterComponentEx)editor.getGutter();
            int x = -gutter.getWidth() + gutter.getLineNumberAreaOffset() + gutter.getLineNumberAreaWidth() / 2;
            int y = editor.offsetToXY(position.getOffset()).y + editor.getLineHeight() / 2;
            DebuggerUIUtil.showXBreakpointEditorBalloon(project, new Point(x, y), editor.getContentComponent(), false, bp);
          });
        }
      });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(ExperimentalUI.isNewUI() && getLineBreakpointPosition(e) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static @Nullable XSourcePosition getLineBreakpointPosition(AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project != null && editor != null && file != null) {
      EditorGutter gutter = editor.getGutter();
      if (gutter instanceof EditorGutterComponentEx) {
        Object lineNumber = ((EditorGutterComponentEx)gutter).getClientProperty("active.line.number");
        if (!(lineNumber instanceof Integer)) {
          lineNumber = e.getData(XDebuggerManagerImpl.ACTIVE_LINE_NUMBER);
        }
        if (lineNumber != null) {
          LogicalPosition pos = new LogicalPosition((Integer)lineNumber, 0);
          return XSourcePositionImpl.createByOffset(file, editor.logicalPositionToOffset(pos));
        }
      }
    }
    return null;
  }

  protected boolean isConditional() {
    return false;
  }

  public static class WithCondition extends AddLineBreakpointAction {
    @Override
    protected boolean isConditional() {
      return true;
    }
  }
}
