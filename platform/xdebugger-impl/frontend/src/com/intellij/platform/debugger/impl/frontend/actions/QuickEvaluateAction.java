// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorGutter;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.XQuickEvaluateHandler;
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.common.ValueLookupManager;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.platform.debugger.impl.frontend.actions.CustomQuickEvaluateActionProviderKt.getEnabledCustomQuickEvaluateActionHandler;

@ApiStatus.Internal
public class QuickEvaluateAction extends DumbAwareAction implements ActionRemoteBehaviorSpecification.Frontend {
  private static final XQuickEvaluateHandler ourXQuickEvaluateHandler = new XQuickEvaluateHandler();

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setVisible(!e.isFromContextMenu());
      e.getPresentation().setEnabled(false);
      return;
    }
    boolean enabled = isEnabled(project, e);
    if (e.isFromContextMenu()) {
      e.getPresentation().setVisible(enabled);
    }
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (project == null || editor == null) {
      return;
    }
    LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
    QuickEvaluateHandler customHandler = getEnabledCustomQuickEvaluateActionHandler(project, e);
    QuickEvaluateHandler handler;
    // first try to use platform's evaluate handler
    if (ourXQuickEvaluateHandler.isEnabled(project, e)) {
      handler = ourXQuickEvaluateHandler;
    }
    else if (customHandler != null) {
      handler = customHandler;
    }
    else {
      return;
    }
    ValueLookupManager.getInstance(project).
      showHint(handler, editor, editor.logicalPositionToXY(logicalPosition), null, ValueHintType.MOUSE_CLICK_HINT);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
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
    if (customHandler != null) {
      return true;
    }

    return false;
  }
}
