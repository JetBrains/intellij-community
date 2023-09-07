// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.i18n.GitBundle;
import git4idea.stash.GitStashOperations;
import git4idea.ui.GitStashDialog;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class GitStash extends GitRepositoryAction {

  @Override
  protected void perform(@NotNull Project project, @NotNull List<VirtualFile> gitRoots, @NotNull VirtualFile defaultRoot) {
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(GitBundle.message("stash.error.can.not.stash.changes.now"))) {
      return;
    }
    GitStashDialog d = new GitStashDialog(project, gitRoots, defaultRoot);
    if (!d.showAndGet()) {
      return;
    }
    d.logUsage();

    GitStashOperations.runStashInBackground(project, Collections.singleton(d.getGitRoot()), root -> d.handler());
  }

  @Override
  @NotNull
  protected String getActionName() {
    return GitBundle.message("stash.action.name");
  }
}
