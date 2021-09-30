// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo;

import com.intellij.openapi.GitRepositoryInitializer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.actions.GitInit;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

import static git4idea.GitNotificationIdsHolder.INIT_FAILED;

public class GitRepositoryInitializerImpl implements GitRepositoryInitializer {
  @Override
  public void initRepository(@NotNull Project project, @NotNull VirtualFile root) {
    ProgressManager.progress2(GitBundle.message("progress.title.creating.git.repository"));

    GitCommandResult result = Git.getInstance().init(project, root);
    if (!result.success()) {
      VcsNotifier.getInstance(project).notifyError(INIT_FAILED, GitBundle.message("action.Git.Init.error"),
                                                   result.getErrorOutputAsHtmlString(), true);
      return;
    }

    GitInit.refreshAndConfigureVcsMappings(project, root, root.getPath());
    GitUtil.generateGitignoreFileIfNeeded(project, root);
  }
}
