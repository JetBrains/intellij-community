// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.XQuickEvaluateHandler;
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.common.ValueLookupManager;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import org.jetbrains.annotations.NotNull;

public class QuickEvaluateAction extends XDebuggerActionBase {
  public QuickEvaluateAction() {
    super(true);
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler(final @NotNull DebuggerSupport debuggerSupport) {
    return new QuickEvaluateHandlerWrapper(debuggerSupport.getQuickEvaluateHandler());
  }

  private static class QuickEvaluateHandlerWrapper extends DebuggerActionHandler {
    private final QuickEvaluateHandler myHandler;
    private final QuickEvaluateHandler myXQuickEvaluateHandler = new XQuickEvaluateHandler();

    QuickEvaluateHandlerWrapper(final QuickEvaluateHandler handler) {
      myHandler = handler;
    }

    @Override
    public void perform(@NotNull Project project, @NotNull AnActionEvent event) {
      Editor editor = event.getData(CommonDataKeys.EDITOR);
      if (editor != null) {
        LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
        QuickEvaluateHandler handler;
        // first try to use platform's evaluate handler
        if (myXQuickEvaluateHandler.isEnabled(project, event)) {
          handler = myXQuickEvaluateHandler;
        }
        else {
          handler = myHandler;
        }
        ValueLookupManager.getInstance(project).
          showHint(handler, editor, editor.logicalPositionToXY(logicalPosition), null, ValueHintType.MOUSE_CLICK_HINT);
      }
    }

    @Override
    public boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
      if (!myHandler.isEnabled(project, event) && !myXQuickEvaluateHandler.isEnabled(project, event)) {
        return false;
      }

      Editor editor = event.getData(CommonDataKeys.EDITOR);
      if (editor == null) {
        return false;
      }

      if (event.getData(EditorGutter.KEY) != null) {
        return false;
      }

      return true;
    }
  }
}
