// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.fileTypes;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import static com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageCounterCollector.triggerEdit;

class FileTypeUsageActionListener implements AnActionListener {
  private static final Key<Long> LAST_EDIT_USAGE = Key.create("LAST_EDIT_USAGE");

  @Override
  public void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
    if (action instanceof EditorAction && ((EditorAction)action).getHandlerOfType(EditorWriteActionHandler.class) != null) {
      onChange(event.getDataContext());
    }
  }

  private static void onChange(DataContext dataContext) {
    Editor editor = CommonDataKeys.HOST_EDITOR.getData(dataContext);
    if (editor == null) return;
    Project project = editor.getProject();
    if (project == null) return;
    VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (file != null) {
      Long lastEdit = editor.getUserData(LAST_EDIT_USAGE);
      if (lastEdit == null || System.currentTimeMillis() - lastEdit > 60 * 1000) {
        editor.putUserData(LAST_EDIT_USAGE, System.currentTimeMillis());
        triggerEdit(project, file);
      }
    }
  }

  @Override
  public void beforeEditorTyping(char c, @NotNull DataContext dataContext) {
    onChange(dataContext);
  }
}