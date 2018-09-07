/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
public class GithubCreatePullRequestAction extends AbstractGithubUrlGroupingAction {
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