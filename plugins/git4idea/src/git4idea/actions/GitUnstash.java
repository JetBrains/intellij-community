// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.i18n.GitBundle;
import git4idea.ui.GitUnstashDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Git unstash action
 */
public class GitUnstash extends GitRepositoryAction {

  /**
   * {@inheritDoc}
   */
  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.getString("unstash.action.name");
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot) {
    final ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.isFreezedWithNotification("Can not unstash changes now")) return;
    GitUnstashDialog.showUnstashDialog(project, gitRoots, defaultRoot);
  }
}
