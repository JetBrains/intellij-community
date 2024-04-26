// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitTagDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Git "tag" action
 */
public class GitTag extends GitRepositoryAction {
  /**
   * {@inheritDoc}
   */
  @Override
  protected @NotNull String getActionName() {
    return GitBundle.message("tag.action.name");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void perform(final @NotNull Project project,
                         final @NotNull List<VirtualFile> gitRoots,
                         final @NotNull VirtualFile defaultRoot) {
    GitTagDialog d = new GitTagDialog(project, gitRoots, defaultRoot);
    if (d.showAndGet()) {
      new Task.Modal(project, GitBundle.message("tag.progress.title"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          d.runAction();
        }
      }.queue();
    }
  }
}
