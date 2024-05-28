// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.codeWithMe.ClientId;
import com.intellij.icons.AllIcons;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.ClientEditorManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScrollToTheEndToolbarAction extends AnAction implements DumbAware {
  private final Editor myEditor;

  public ScrollToTheEndToolbarAction(final @Nullable Editor editor) {
    super();
    myEditor = editor;
    final String message = ActionsBundle.message("action.EditorConsoleScrollToTheEnd.text");
    getTemplatePresentation().setDescription(message);
    getTemplatePresentation().setText(message);
    getTemplatePresentation().setIcon(AllIcons.RunConfigurations.Scroll_down);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (myEditor == null) return;
    Editor editor  = ClientEditorManager.Companion.getClientEditor(myEditor, ClientId.getCurrentOrNull());
    EditorUtil.scrollToTheEnd(editor);
  }
}
