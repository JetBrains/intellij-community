// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class ShowHideRecycledAction extends ToggleAction implements DumbAware {

  ShowHideRecycledAction() {
    super(VcsBundle.messagePointer("shelve.show.already.unshelved.action"));
  }
  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);
    final Project project = getEventProject(e);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(project != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    final Project project = getEventProject(e);
    return project != null && ShelveChangesManager.getInstance(project).isShowRecycled();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    final Project project = getEventProject(e);
    if (project != null) {
      ShelveChangesManager.getInstance(project).setShowRecycled(state);
      ShelvedChangesViewManager.getInstance(project).updateTreeView();
    }
  }
}
