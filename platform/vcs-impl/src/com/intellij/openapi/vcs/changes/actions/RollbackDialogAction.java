// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ui.RollbackChangesDialog;
import com.intellij.vcsUtil.RollbackUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * @author yole
*/
public class RollbackDialogAction extends AnAction implements DumbAware {
  public RollbackDialogAction() {
    ActionUtil.copyFrom(this, IdeActions.CHANGES_VIEW_ROLLBACK);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();
    Change[] changes = e.getRequiredData(VcsDataKeys.CHANGES);
    Project project = e.getData(CommonDataKeys.PROJECT);
    RollbackChangesDialog.rollbackChanges(project, Arrays.asList(changes));
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
      e.getPresentation().setDescription(operationName + " selected changes");
    }

  }
}
