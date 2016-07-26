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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import git4idea.GitFileRevision;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.util.List;

public class GithubShowCommitInBrowserAction extends DumbAwareAction {
  public GithubShowCommitInBrowserAction() {
    super("Open commit on GitHub", "Open selected commit in browser", GithubIcons.Github_icon);
  }

  @Override
  public void update(AnActionEvent e) {
    CommitData data = getData(e);
    e.getPresentation().setEnabled(data != null && data.revisionHash != null);
    e.getPresentation().setVisible(data != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    CommitData data = getData(e);
    assert data != null && data.revisionHash != null;
    openInBrowser(data.project, data.repository, data.revisionHash);
  }

  protected static void openInBrowser(@NotNull Project project, @NotNull GitRepository repository, @NotNull String revisionHash) {
    String url = GithubUtil.findGithubRemoteUrl(repository);
    if (url == null) {
      GithubUtil.LOG.info(String.format("Repository is not under GitHub. Root: %s, Remotes: %s", repository.getRoot(),
                                        GitUtil.getPrintableRemotes(repository.getRemotes())));
      return;
    }
    GithubFullPath userAndRepository = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
    if (userAndRepository == null) {
      GithubNotifications
        .showError(project, GithubOpenInBrowserAction.CANNOT_OPEN_IN_BROWSER, "Can't extract info about repository: " + url);
      return;
    }

    String githubUrl = GithubUrlUtil.getGithubHost() + '/' + userAndRepository.getUser() + '/'
                       + userAndRepository.getRepository() + "/commit/" + revisionHash;
    BrowserUtil.browse(githubUrl);
  }

  @Nullable
  protected CommitData getData(AnActionEvent e) {
    CommitData data = getDataFromHistory(e);
    if (data == null) data = getDataFromLog(e);
    return data;
  }

  @Nullable
  private static CommitData getDataFromHistory(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    FilePath filePath = e.getData(VcsDataKeys.FILE_PATH);
    VcsFileRevision fileRevision = e.getData(VcsDataKeys.VCS_FILE_REVISION);
    if (project == null || filePath == null || fileRevision == null) return null;

    if (!(fileRevision instanceof GitFileRevision)) return null;

    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForFile(filePath);
    if (repository == null || !GithubUtil.isRepositoryOnGitHub(repository)) return null;

    return new CommitData(project, repository, fileRevision.getRevisionNumber().asString());
  }

  @Nullable
  private static CommitData getDataFromLog(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    if (project == null || log == null) return null;

    List<CommitId> selectedCommits = log.getSelectedCommits();
    if (selectedCommits.size() != 1) return null;

    CommitId commit = ContainerUtil.getFirstItem(selectedCommits);
    if (commit == null) return null;

    GitRepository repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(commit.getRoot());
    if (repository == null || !GithubUtil.isRepositoryOnGitHub(repository)) return null;

    return new CommitData(project, repository, commit.getHash().asString());
  }

  protected static class CommitData {
    @NotNull private final Project project;
    @NotNull private final GitRepository repository;
    @Nullable private final String revisionHash;

    public CommitData(@NotNull Project project, @NotNull GitRepository repository, @Nullable String revisionHash) {
      this.project = project;
      this.repository = repository;
      this.revisionHash = revisionHash;
    }
  }
}
