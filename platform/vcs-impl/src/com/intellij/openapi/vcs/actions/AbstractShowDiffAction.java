// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
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

import static com.intellij.openapi.util.text.StringUtil.removeEllipsisSuffix;
import static com.intellij.util.containers.UtilKt.getIfSingle;
import static com.intellij.util.ui.UIUtil.removeMnemonic;

public abstract class AbstractShowDiffAction extends AbstractVcsAction {
  @Override
  protected void update(@NotNull VcsContext vcsContext, @NotNull Presentation presentation) {
    updateDiffAction(presentation, vcsContext);
  }

  protected static void updateDiffAction(@NotNull Presentation presentation,
                                         @NotNull VcsContext vcsContext) {
    presentation.setEnabled(isEnabled(vcsContext, true));
    presentation.setVisible(isVisible(vcsContext));
  }

  protected static boolean isVisible(@NotNull VcsContext vcsContext) {
    Project project = vcsContext.getProject();
    return project != null && hasDiffProviders(project);
  }

  private static boolean hasDiffProviders(@NotNull Project project) {
    return Stream.of(ProjectLevelVcsManager.getInstance(project).getAllActiveVcss())
      .map(AbstractVcs::getDiffProvider)
      .anyMatch(Objects::nonNull);
  }

  protected static boolean isEnabled(@NotNull VcsContext vcsContext, boolean disableIfRunning) {
    Project project = vcsContext.getProject();
    if (project == null) return false;

    if (!isVisible(vcsContext)) return false;

    VirtualFile file = getIfSingle(vcsContext.getSelectedFilesStream());
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
  protected void actionPerformed(@NotNull VcsContext vcsContext) {
    Project project = Objects.requireNonNull(vcsContext.getProject());

    String actionName = removeEllipsisSuffix(removeMnemonic(vcsContext.getActionName()));
    if (!ChangeListManager.getInstance(project).isFreezedWithNotification(VcsBundle.message("error.cant.perform.operation.now", actionName))) {
      VirtualFile file = vcsContext.getSelectedFiles()[0];
      AbstractVcs vcs = Objects.requireNonNull(ChangesUtil.getVcsForFile(file, project));
      DiffProvider provider = Objects.requireNonNull(vcs.getDiffProvider());
      Editor editor = vcsContext.getEditor();

      getExecutor(provider, file, project, editor).showDiff();
    }
  }

  @NotNull
  protected abstract DiffActionExecutor getExecutor(@NotNull DiffProvider diffProvider,
                                                    @NotNull VirtualFile selectedFile,
                                                    @NotNull Project project,
                                                    @Nullable Editor editor);
}
