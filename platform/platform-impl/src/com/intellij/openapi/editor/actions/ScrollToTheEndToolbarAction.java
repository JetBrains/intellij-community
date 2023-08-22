// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class ScrollToTheEndToolbarAction extends ToggleAction implements DumbAware {
  private final Editor myEditor;

  public ScrollToTheEndToolbarAction(final @NotNull Editor editor) {
    super();
    myEditor = editor;
    final String message = ActionsBundle.message("action.EditorConsoleScrollToTheEnd.text");
    getTemplatePresentation().setDescription(message);
    getTemplatePresentation().setText(message);
    getTemplatePresentation().setIcon(AllIcons.RunConfigurations.Scroll_down);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Document document = myEditor.getDocument();
    return document.getLineCount() == 0 || document.getLineNumber(myEditor.getCaretModel().getOffset()) == document.getLineCount() - 1;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    if (state) {
      EditorUtil.scrollToTheEnd(myEditor);
    } else {
      int lastLine = Math.max(0, myEditor.getDocument().getLineCount() - 1);
      LogicalPosition currentPosition = myEditor.getCaretModel().getLogicalPosition();
      LogicalPosition position = new LogicalPosition(Math.max(0, Math.min(currentPosition.line, lastLine - 1)), currentPosition.column);
      myEditor.getCaretModel().moveToLogicalPosition(position);
    }
  }
}
