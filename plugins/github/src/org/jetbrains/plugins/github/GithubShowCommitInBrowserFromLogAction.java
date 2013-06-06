/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.history.browser.GitHeavyCommit;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public class GithubShowCommitInBrowserFromLogAction extends GithubShowCommitInBrowserAction {

  @Override
  public void update(AnActionEvent e) {
    EventData eventData = collectData(e);
    e.getPresentation().setVisible(eventData != null && GithubUtil.isRepositoryOnGitHub(eventData.getRepository()));
    e.getPresentation().setEnabled(eventData != null);
  }

  @Nullable
  private static EventData collectData(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null || project.isDefault()) {
      return null;
    }

    GitHeavyCommit commit = e.getData(GitVcs.GIT_COMMIT);
    if (commit == null) {
      return null;
    }

    VirtualFile root = commit.getRoot();
    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(root);
    if (repository == null) {
      return null;
    }

    return new EventData(project, repository, commit);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    EventData eventData = collectData(e);
    if (eventData != null) {
      openInBrowser(eventData.getProject(), eventData.getRepository(), eventData.getCommit().getHash().getValue());
    }
  }

  private static class EventData {
    @NotNull private final Project myProject;
    @NotNull private final GitRepository myRepository;
    @NotNull private final GitHeavyCommit myCommit;

    private EventData(@NotNull Project project, @NotNull GitRepository repository, @NotNull GitHeavyCommit commit) {
      myProject = project;
      myRepository = repository;
      myCommit = commit;
    }

    @NotNull
    public Project getProject() {
      return myProject;
    }

    @NotNull
    public GitRepository getRepository() {
      return myRepository;
    }

    @NotNull
    public GitHeavyCommit getCommit() {
      return myCommit;
    }
  }

}
