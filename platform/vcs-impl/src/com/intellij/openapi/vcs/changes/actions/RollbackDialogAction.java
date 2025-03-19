// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.vcsUtil.RollbackUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class RollbackDialogAction extends AnAction implements DumbAware {
  public RollbackDialogAction() {
    ActionUtil.copyFrom(this, IdeActions.CHANGES_VIEW_ROLLBACK);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    if (changes == null) return;
    RollbackChangesDialog.rollbackChanges(project, Arrays.asList(changes));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Change[] changes = e.getData(VcsDataKeys.CHANGES);
    Project project = e.getData(CommonDataKeys.PROJECT);
    boolean enabled = changes != null && project != null;
    e.getPresentation().setEnabled(enabled);
    if (enabled) {
      String operationName = RollbackUtil.getRollbackOperationName(project);
      e.getPresentation().setText(operationName + "...");
      e.getPresentation().setDescription(VcsBundle.message("action.message.use.selected.changes.description", operationName));
    }

  }
}
