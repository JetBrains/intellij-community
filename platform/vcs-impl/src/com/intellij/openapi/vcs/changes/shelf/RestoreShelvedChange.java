// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;

public class RestoreShelvedChange extends DumbAwareAction {
  public RestoreShelvedChange() {
    super("Restore");
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final ShelvedChangeList[] recycledChanges = e.getData(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY);
    e.getPresentation().setText(VcsBundle.message("vcs.shelf.action.restore.text"));
    e.getPresentation().setDescription(VcsBundle.message("vcs.shelf.action.restore.description"));
    e.getPresentation().setEnabled((project != null) && ((recycledChanges != null) && (recycledChanges.length == 1)));
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final ShelvedChangeList[] recycledChanges = e.getData(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY);
    if (recycledChanges != null && recycledChanges.length == 1) {
      ShelveChangesManager.getInstance(project).restoreList(recycledChanges[0]);
    }
  }
}
