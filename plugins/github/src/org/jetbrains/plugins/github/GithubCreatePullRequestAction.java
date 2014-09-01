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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.DialogManager;
import git4idea.repo.GitRepository;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog;
import org.jetbrains.plugins.github.util.GithubUtil;

import static org.jetbrains.plugins.github.util.GithubUtil.setVisibleEnabled;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestAction extends DumbAwareAction {
  public GithubCreatePullRequestAction() {
    super("Create Pull Request", "Create pull request from current branch", GithubIcons.Github_icon);
  }

  public void update(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project == null || project.isDefault()) {
      setVisibleEnabled(e, false, false);
      return;
    }

    final GitRepository gitRepository = GithubUtil.getGitRepository(project, file);
    if (gitRepository == null) {
      setVisibleEnabled(e, false, false);
      return;
    }

    if (!GithubUtil.isRepositoryOnGitHub(gitRepository)) {
      setVisibleEnabled(e, false, false);
      return;
    }

    setVisibleEnabled(e, true, true);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

    if (project == null || project.isDisposed() || !GithubUtil.testGitExecutable(project)) {
      return;
    }

    createPullRequest(project, file);
  }

  static void createPullRequest(@NotNull Project project, @Nullable VirtualFile file) {
    GithubCreatePullRequestWorker worker = GithubCreatePullRequestWorker.create(project, file);
    if (worker == null) {
      return;
    }

    GithubCreatePullRequestDialog dialog = new GithubCreatePullRequestDialog(project, worker);
    DialogManager.show(dialog);
  }
}