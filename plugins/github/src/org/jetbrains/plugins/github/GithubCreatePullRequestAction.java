// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import git4idea.DialogManager;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestAction extends AbstractAuthenticatingGithubUrlGroupingAction {
  public GithubCreatePullRequestAction() {
    super("Create Pull Request", "Create pull request from current branch", AllIcons.Vcs.Vendors.Github);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e,
                              @NotNull Project project,
                              @NotNull GitRepository repository,
                              @NotNull GitRemote remote,
                              @NotNull String remoteUrl,
                              @NotNull GithubAccount account) {
    createPullRequest(project, repository, remote, remoteUrl, account);
  }

  static void createPullRequest(@NotNull Project project,
                                @NotNull GitRepository gitRepository,
                                @NotNull GitRemote remote,
                                @NotNull String remoteUrl,
                                @NotNull GithubAccount account) {
    GithubApiRequestExecutor executor = GithubApiRequestExecutorManager.getInstance().getExecutor(account, project);
    if (executor == null) return;

    GithubCreatePullRequestWorker worker = GithubCreatePullRequestWorker.create(project, gitRepository, remote, remoteUrl,
                                                                                executor, account.getServer());
    if (worker == null) {
      return;
    }

    GithubCreatePullRequestDialog dialog = new GithubCreatePullRequestDialog(project, worker);
    DialogManager.show(dialog);
  }
}