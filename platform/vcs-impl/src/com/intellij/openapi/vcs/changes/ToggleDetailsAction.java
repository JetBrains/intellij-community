// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.changes.actions.ShowDiffPreviewAction;
import org.jetbrains.annotations.NotNull;

public class ToggleDetailsAction extends ShowDiffPreviewAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    if (project == null) return;
    ChangesViewController controller = e.getData(ChangesViewController.DATA_KEY);
    if (controller == null) return;
    e.getPresentation().setEnabledAndVisible(controller.isDiffPreviewAvailable()
                                             || Registry.get("show.diff.preview.as.editor.tab.with.single.click").asBoolean());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project == null) return;
    VcsConfiguration.getInstance(project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN = state;

    ChangesViewController controller = e.getData(ChangesViewController.DATA_KEY);
    if (controller == null) return;
    controller.toggleDiffPreview(state);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return false;

    return VcsConfiguration.getInstance(project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN;
  }
}
