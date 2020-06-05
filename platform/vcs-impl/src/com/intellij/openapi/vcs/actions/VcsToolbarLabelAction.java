// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsToolbarLabelAction extends ToolbarLabelAction {
  private static final String DEFAULT_LABEL = "VCS:";

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    Project project = e.getProject();
    e.getPresentation().setVisible(project != null && ProjectLevelVcsManager.getInstance(project).hasActiveVcss());
    e.getPresentation().setText(getConsolidatedVcsName(project));
  }

  private static String getConsolidatedVcsName(@Nullable Project project) {
    String name = DEFAULT_LABEL;
    if (project != null) {
      name = ProjectLevelVcsManager.getInstance(project).getConsolidatedVcsName() + ":";
    }
    return name;
  }
}
