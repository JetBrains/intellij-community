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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.DialogManager;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestAction extends LegacySingleAccountActionGroup {
  public GithubCreatePullRequestAction() {
    super("Create Pull Request", "Create pull request from current branch", GithubIcons.Github_icon);
  }

  @Override
  public void actionPerformed(@NotNull Project project,
                              @Nullable VirtualFile file,
                              @NotNull GitRepository gitRepository,
                              @NotNull GithubAccount account) {
    createPullRequest(project, gitRepository, account);
  }

  @Nullable
  @Override
  protected Pair<GitRemote, String> getRemote(@NotNull GithubServerPath server, @NotNull GitRepository repository) {
    return GithubCreatePullRequestWorker.findGithubRemote(server, repository);
  }

  static void createPullRequest(@NotNull Project project,
                                @NotNull GitRepository gitRepository,
                                @NotNull GithubAccount account) {
    GithubCreatePullRequestWorker worker = GithubCreatePullRequestWorker.create(project, gitRepository, account);
    if (worker == null) {
      return;
    }

    GithubCreatePullRequestDialog dialog = new GithubCreatePullRequestDialog(project, worker);
    DialogManager.show(dialog);
  }
}