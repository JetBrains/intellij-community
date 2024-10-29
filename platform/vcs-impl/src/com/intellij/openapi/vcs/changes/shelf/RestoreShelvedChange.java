// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.isEmpty;
import static com.intellij.util.containers.ContainerUtil.notNullize;

@ApiStatus.Internal
public class RestoreShelvedChange extends DumbAwareAction {
  public RestoreShelvedChange() {
    super(ActionsBundle.messagePointer("action.RestoreShelvedChange.text"));
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    Presentation presentation = e.getPresentation();
    if (project == null || project.isDisposed()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    Collection<ShelvedChangeList> deletedLists = notNullize(e.getData(ShelvedChangesViewManager.SHELVED_DELETED_CHANGELIST_KEY));
    presentation.setText(VcsBundle.messagePointer("vcs.shelf.action.restore.text"));
    presentation
      .setDescription(VcsBundle.messagePointer("vcs.shelf.action.restore.description", deletedLists.size()));
    presentation.setEnabled(!isEmpty(deletedLists));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    List<ShelvedChangeList> lists = e.getData(ShelvedChangesViewManager.SHELVED_DELETED_CHANGELIST_KEY);
    if (lists == null || lists.isEmpty()) return;
    ShelveChangesManager shelveChangesManager = ShelveChangesManager.getInstance(project);
    Date currentDate = new Date(System.currentTimeMillis());
    lists.forEach(l -> shelveChangesManager.restoreList(l, currentDate));
  }
}
