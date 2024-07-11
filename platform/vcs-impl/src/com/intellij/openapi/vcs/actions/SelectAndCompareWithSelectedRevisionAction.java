// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.diff.RevisionSelector;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SelectAndCompareWithSelectedRevisionAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    final VirtualFile file = VcsContextUtil.selectedFile(e.getDataContext());
    if (project == null || file == null) return;

    final AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file);
    if (vcs == null) return;

    RevisionSelector selector = vcs.getRevisionSelector();
    DiffProvider diffProvider = vcs.getDiffProvider();
    if (selector == null || diffProvider == null) return;

    VcsRevisionNumber vcsRevisionNumber = selector.selectNumber(file);
    if (vcsRevisionNumber != null) {
      DiffActionExecutor.showDiff(diffProvider, vcsRevisionNumber, file, project);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean isVisible = isVisible(e.getDataContext());
    e.getPresentation().setEnabled(isVisible && isEnabled(e.getDataContext()));
    e.getPresentation().setVisible(isVisible);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static boolean isVisible(@NotNull DataContext context) {
    Project project = context.getData(CommonDataKeys.PROJECT);
    if (project == null) return false;

    AbstractVcs[] vcss = ProjectLevelVcsManager.getInstance(project).getAllActiveVcss();
    return ContainerUtil.exists(vcss, SelectAndCompareWithSelectedRevisionAction::canShowDiffForVcs);
  }

  private static boolean isEnabled(@NotNull DataContext context) {
    Project project = context.getData(CommonDataKeys.PROJECT);
    VirtualFile file = VcsContextUtil.selectedFile(context);
    if (project == null || file == null || file.isDirectory()) return false;

    AbstractVcs vcs = ChangesUtil.getVcsForFile(file, project);
    if (!canShowDiffForVcs(vcs)) return false;

    if (!AbstractVcs.fileInVcsByFileStatus(project, file)) return false;

    return true;
  }

  private static boolean canShowDiffForVcs(@Nullable AbstractVcs vcs) {
    return vcs != null && vcs.getDiffProvider() != null && vcs.getRevisionSelector() != null;
  }
}
