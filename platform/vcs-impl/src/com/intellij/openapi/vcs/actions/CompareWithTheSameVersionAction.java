// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompareWithTheSameVersionAction extends AbstractShowDiffAction {
  @Override
  @NotNull
  protected DiffActionExecutor getExecutor(@NotNull DiffProvider diffProvider,
                                           @NotNull VirtualFile selectedFile,
                                           @NotNull Project project,
                                           @Nullable Editor editor) {
    return new DiffActionExecutor.CompareToCurrentExecutor(diffProvider, selectedFile, project, editor);
  }

  @Override
  protected void update(@NotNull VcsContext vcsContext, @NotNull Presentation presentation) {
    super.update(vcsContext, presentation);
    Project project = vcsContext.getProject();
    presentation.setText(ActionsBundle.message("action.Compare.SameVersion.text"));

    if (project != null) {
      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getSingleVCS();
      if (vcs != null) {
        String customDiffName = vcs.getCompareWithTheSameVersionActionName();
        if (customDiffName != null) {
          presentation.setText(customDiffName);
        }
      }
    }
  }
}
