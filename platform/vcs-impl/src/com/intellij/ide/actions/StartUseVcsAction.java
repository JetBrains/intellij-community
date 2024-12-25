// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class StartUseVcsAction extends DumbAwareAction {
  public StartUseVcsAction() {
    super(VcsBundle.messagePointer("action.enable.version.control.integration.text"));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(guessDirectory(e) != null);
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    @Nullable VirtualFile directory = guessDirectory(e);
    if (directory == null) return;

    StartUseVcsDialog dialog = new StartUseVcsDialog(project, directory.getPath());
    if (dialog.showAndGet()) {
      AbstractVcs vcs = dialog.getVcs();
      vcs.enableIntegration(directory);
    }
  }

  protected @Nullable VirtualFile guessDirectory(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || !TrustedProjects.isTrusted(project)) return null;
    ProjectLevelVcsManagerImpl manager = ProjectLevelVcsManagerImpl.getInstanceImpl(project);
    if (manager.haveVcses() && !manager.hasAnyMappings()) {
      VirtualFile targetDirectory = ProjectUtil.guessProjectDir(project);
      if (targetDirectory == null) {
        return null;
      }
      return targetDirectory;
    }
    return null;
  }
}
