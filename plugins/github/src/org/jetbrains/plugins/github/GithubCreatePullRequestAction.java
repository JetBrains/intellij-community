// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.github.i18n.GithubBundle;
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GitRemoteUrlCoordinates;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestAction extends AbstractAuthenticatingGithubUrlGroupingAction {
  public GithubCreatePullRequestAction() {
    super(GithubBundle.messagePointer("pull.request.create.action"),
          GithubBundle.messagePointer("pull.request.create.action.description"),
          AllIcons.Vcs.Vendors.Github);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e,
                              @NotNull Project project,
                              @NotNull GHGitRepositoryMapping repository,
                              @NotNull GithubAccount account) {
    GitRemoteUrlCoordinates remoteCoordinates = repository.getRemote();
    createPullRequest(project, remoteCoordinates.getRepository(), remoteCoordinates.getRemote(), remoteCoordinates.getUrl(), account);
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