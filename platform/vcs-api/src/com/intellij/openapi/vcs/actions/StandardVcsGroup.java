// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class StandardVcsGroup extends DefaultActionGroup implements DumbAware {
  public abstract AbstractVcs getVcs(Project project);

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      final String vcsName = getVcsName(project);
      presentation.setVisible(vcsName != null &&
                              ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(vcsName));
    }
    else {
      presentation.setVisible(false);
    }
    presentation.setEnabled(presentation.isVisible());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public @Nullable @NonNls String getVcsName(Project project) {
    final AbstractVcs vcs = getVcs(project);
    // if the parent group was customized and then the plugin was disabled, we could have an action group with no VCS
    return vcs != null ? vcs.getName() : null;
  }
}
