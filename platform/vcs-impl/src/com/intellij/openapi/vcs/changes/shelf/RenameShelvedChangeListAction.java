// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public class RenameShelvedChangeListAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    List<ShelvedChangeList> changelists = ShelvedChangesViewManager.getShelvedLists(e.getDataContext());
    ShelvedChangeList changeList = Objects.requireNonNull(ContainerUtil.getFirstItem(changelists));
    ShelvedChangesViewManager.getInstance(project).startEditing(changeList);
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    e.getPresentation().setEnabled(getEventProject(e) != null && ShelvedChangesViewManager.getShelvedLists(e.getDataContext()).size() == 1);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
