// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.i18n.GitBundle;
import git4idea.stash.ui.GitStashContentProvider;
import git4idea.ui.GitUnstashDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static git4idea.stash.ui.GitStashContentProviderKt.*;

/**
 * Git unstash action
 */
public class GitUnstash extends GitRepositoryAction {

  /**
   * {@inheritDoc}
   */
  @Override
  protected @NotNull String getActionName() {
    return GitBundle.message("unstash.action.name");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void perform(final @NotNull Project project,
                         final @NotNull List<VirtualFile> gitRoots,
                         final @NotNull VirtualFile defaultRoot) {
    if (isStashTabAvailable() && ChangesViewContentManager.getToolWindowFor(project, GitStashContentProvider.TAB_NAME) != null) {
      showStashes(project, defaultRoot);
      return;
    }
    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.isFreezedWithNotification(GitBundle.message("unstash.error.can.not.unstash.changes.now"))) return;
    GitUnstashDialog.showUnstashDialog(project, gitRoots, defaultRoot);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Project project = e.getProject();
    if (project != null && isStashTabAvailable() && !isStashTabVisible(project)) {
      e.getPresentation().setEnabled(false);
    }
  }
}
