// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public abstract class AbstractShowDiffAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    updateDiffAction(e.getPresentation(), e.getDataContext());
  }

  protected static void updateDiffAction(@NotNull Presentation presentation,
                                         @NotNull DataContext context) {
    presentation.setEnabled(isEnabled(context, true));
    presentation.setVisible(isVisible(context));
  }

  protected static boolean isVisible(@NotNull DataContext context) {
    Project project = context.getData(CommonDataKeys.PROJECT);
    return project != null && hasDiffProviders(project);
  }

  private static boolean hasDiffProviders(@NotNull Project project) {
    return Stream.of(ProjectLevelVcsManager.getInstance(project).getAllActiveVcss())
      .map(AbstractVcs::getDiffProvider)
      .anyMatch(Objects::nonNull);
  }

  protected static boolean isEnabled(@NotNull DataContext context, boolean disableIfRunning) {
    Project project = context.getData(CommonDataKeys.PROJECT);
    if (project == null) return false;

    if (!isVisible(context)) return false;

    VirtualFile file = VcsContextUtil.selectedFilesIterable(context).single();
    if (file == null || file.isDirectory()) return false;

    FilePath filePath = VcsUtil.getFilePath(file);

    if (disableIfRunning) {
      if (BackgroundableActionLock.isLocked(project, VcsBackgroundableActions.COMPARE_WITH, filePath)) {
        return false;
      }
    }

    AbstractVcs vcs = ChangesUtil.getVcsForFile(file, project);
    if (vcs == null || vcs.getDiffProvider() == null) return false;

    if (!AbstractVcs.fileInVcsByFileStatus(project, filePath)) return false;

    return true;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(null)) {
      return;
    }

    VirtualFile file = requireNonNull(VcsContextUtil.selectedFile(e.getDataContext()));
    AbstractVcs vcs = requireNonNull(ChangesUtil.getVcsForFile(file, project));
    DiffProvider provider = requireNonNull(vcs.getDiffProvider());
    Editor editor = e.getData(CommonDataKeys.EDITOR);

    getExecutor(provider, file, project, editor).showDiff();
  }

  @NotNull
  protected abstract DiffActionExecutor getExecutor(@NotNull DiffProvider diffProvider,
                                                    @NotNull VirtualFile selectedFile,
                                                    @NotNull Project project,
                                                    @Nullable Editor editor);
}
