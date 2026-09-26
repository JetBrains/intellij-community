// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.XQuickEvaluateHandler;
import com.intellij.platform.debugger.impl.ui.evaluate.quick.common.ValueLookupManager;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.intellij.xdebugger.impl.actions.XDebuggerActionBase;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.debugger.impl.ui.actions.CustomQuickEvaluateActionProviderKt.getEnabledCustomQuickEvaluateActionHandler;

@ApiStatus.Internal
public class QuickEvaluateAction extends XDebuggerActionBase implements ActionRemoteBehaviorSpecification.Frontend {
  private static final QuickEvaluateActionHandler ourHandler = new QuickEvaluateActionHandler();

  public QuickEvaluateAction() {
    super(true);
  }

  @Override
  protected @NotNull DebuggerActionHandler getHandler() {
    return ourHandler;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    performWithHandler(e);
  }

  private static class QuickEvaluateActionHandler extends DebuggerActionHandler {
    private static final XQuickEvaluateHandler ourXQuickEvaluateHandler = new XQuickEvaluateHandler();

    @Override
    public boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
      Editor editor = event.getData(CommonDataKeys.EDITOR);
      if (editor == null) {
        return false;
      }
      if (event.getData(EditorGutter.KEY) != null) {
        return false;
      }
      if (ourXQuickEvaluateHandler.isEnabled(project, event)) {
        return true;
      }
      QuickEvaluateHandler customHandler = getEnabledCustomQuickEvaluateActionHandler(project, event);
      return customHandler != null;
    }

    @Override
    public void perform(@NotNull Project project, @NotNull AnActionEvent event) {
      Editor editor = event.getData(CommonDataKeys.EDITOR);
      if (editor == null) {
        return;
      }
      LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();

      QuickEvaluateHandler handler;
      // first try to use platform's evaluate handler
      if (ourXQuickEvaluateHandler.isEnabled(project, event)) {
        handler = ourXQuickEvaluateHandler;
      }
      else {
        QuickEvaluateHandler customHandler = getEnabledCustomQuickEvaluateActionHandler(project, event);
        if (customHandler == null) {
          return;
        }
        handler = customHandler;
      }
      ValueLookupManager.getInstance(project).
        showHint(handler, editor, editor.logicalPositionToXY(logicalPosition), null, ValueHintType.MOUSE_CLICK_HINT);
    }
  }
}
