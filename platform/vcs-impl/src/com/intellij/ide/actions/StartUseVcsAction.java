// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.impl.TrustedProjects;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StartUseVcsAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(StartUseVcsAction.class);

  public StartUseVcsAction() {
    super(VcsBundle.messagePointer("action.enable.version.control.integration.text"));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);

    boolean enabled = isEnabled(project);

    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(enabled);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    if (!isEnabled(project)) return;
    VirtualFile targetDirectory = ProjectUtil.guessProjectDir(project);
    if (targetDirectory == null) {
      LOG.warn("Project directory is null");
      return;
    }

    StartUseVcsDialog dialog = new StartUseVcsDialog(project, targetDirectory.getPath());
    if (dialog.showAndGet()) {
      AbstractVcs vcs = dialog.getVcs();
      vcs.enableIntegration();
    }
  }

  private static boolean isEnabled(@Nullable Project project) {
    if (project == null || !TrustedProjects.isTrusted(project)) return false;
    ProjectLevelVcsManagerImpl manager = ProjectLevelVcsManagerImpl.getInstanceImpl(project);
    return manager.haveVcses() && !manager.hasAnyMappings();
  }
}
