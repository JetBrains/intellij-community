// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
final class CloseAllUnmodifiedEditorsAction extends CloseEditorsActionBase {
  @Override
  protected boolean isFileToClose(@NotNull EditorComposite editor, @NotNull EditorWindow window, @NotNull FileEditorManagerEx fileEditorManager) {
    return !fileEditorManager.isChanged(editor) && !window.isFilePinned(editor.getFile());
  }

  @Override
  protected boolean isActionEnabled(Project project, AnActionEvent event) {
    return super.isActionEnabled(project, event) && ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length > 0;
  }

  @Override
  protected String getPresentationText(final boolean inSplitter) {
    if (inSplitter) {
      return IdeBundle.message("action.close.all.unmodified.editors.in.tab.group");
    }
    else {
      return IdeBundle.message("action.close.all.unmodified.editors");
    }
  }
}
