/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import git4idea.GitFileRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.util.GithubUtil;

public class GithubShowCommitInBrowserFromHistoryAction extends GithubShowCommitInBrowserAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    VcsFileRevision fileRevision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (project == null || filePath == null || fileRevision == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForFile(filePath);
    boolean isOnGithub = repository != null && GithubUtil.isRepositoryOnGitHub(repository);
    e.getPresentation().setEnabledAndVisible(isOnGithub && fileRevision instanceof GitFileRevision);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    FilePath filePath = e.getRequiredData(VcsDataKeys.FILE_PATH);
    VcsFileRevision fileRevision = e.getRequiredData(VcsDataKeys.VCS_FILE_REVISION);
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForFile(filePath);
    openInBrowser(project, repository, ((GitRevisionNumber)fileRevision.getRevisionNumber()).getRev());
  }
}
