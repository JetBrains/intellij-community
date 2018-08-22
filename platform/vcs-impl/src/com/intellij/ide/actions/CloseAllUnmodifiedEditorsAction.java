// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;

public class CloseAllUnmodifiedEditorsAction extends CloseEditorsActionBase {

  @Override
  protected boolean isFileToClose(final EditorComposite editor, final EditorWindow window) {
    return !window.getManager().isChanged(editor) && !window.isFilePinned(editor.getFile());
  }

  @Override
  protected boolean isActionEnabled(final Project project, final AnActionEvent event) {
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
