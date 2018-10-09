// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class RestoreShelvedChange extends DumbAwareAction {
  public RestoreShelvedChange() {
    super("Restore");
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Collection<ShelvedChangeList> recycledChanges = e.getData(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY);
    e.getPresentation().setText(VcsBundle.message("vcs.shelf.action.restore.text"));
    e.getPresentation().setDescription(VcsBundle.message("vcs.shelf.action.restore.description"));
    e.getPresentation().setEnabled(project != null && recycledChanges != null);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    final Collection<ShelvedChangeList> recycledChanges = e.getData(ShelvedChangesViewManager.SHELVED_RECYCLED_CHANGELIST_KEY);
    if (recycledChanges != null && recycledChanges.size() == 1) {
      //noinspection ConstantConditions
      ShelveChangesManager.getInstance(project).restoreList(ContainerUtil.getFirstItem(recycledChanges));
    }
  }
}
