// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.troubleshooting.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

final class CollectTroubleshootingInformationAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    new CollectTroubleshootingInformationDialog(project).show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getProject() != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
