// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.i18n.GitBundle;
import git4idea.stash.GitStashOperations;
import git4idea.stash.GitStashUtils;
import git4idea.stash.ui.GitStashDialog;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class GitStash extends GitRepositoryAction {

  @Override
  protected void perform(@NotNull Project project, @NotNull List<VirtualFile> gitRoots, @NotNull VirtualFile defaultRoot) {
    if (ChangeListManager.getInstance(project).isFreezedWithNotification(GitBundle.message("stash.error.can.not.stash.changes.now"))) {
      return;
    }
    GitStashDialog dialog = new GitStashDialog(project, gitRoots, defaultRoot);
    if (!dialog.showAndGet()) return;

    VirtualFile selectedRoot = dialog.getSelectedRoot();
    String message = dialog.getMessage();
    boolean keepIndex = dialog.getKeepIndex();

    GitStashOperations.runStashInBackground(project, Collections.singleton(selectedRoot), root -> {
      return GitStashUtils.createStashHandler(project, root, keepIndex, message);
    });
  }

  @Override
  protected @NotNull String getActionName() {
    return GitBundle.message("stash.action.name");
  }
}
