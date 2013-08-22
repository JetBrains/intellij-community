/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import static org.jetbrains.plugins.github.util.GithubUtil.setVisibleEnabled;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date 12/10/10
 */
public class GithubOpenInBrowserAction extends DumbAwareAction {
  public static final String CANNOT_OPEN_IN_BROWSER = "Cannot open in browser";

  protected GithubOpenInBrowserAction() {
    super("Open on GitHub", "Open corresponding link in browser", GithubIcons.Github_icon);
  }

  @Override
  public void update(final AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (project == null || project.isDefault() || virtualFile == null) {
      setVisibleEnabled(e, false, false);
      return;
    }
    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);

    final GitRepository gitRepository = manager.getRepositoryForFile(virtualFile);
    if (gitRepository == null) {
      setVisibleEnabled(e, false, false);
      return;
    }

    if (!GithubUtil.isRepositoryOnGitHub(gitRepository)) {
      setVisibleEnabled(e, false, false);
      return;
    }

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    if (changeListManager.isUnversioned(virtualFile)) {
      setVisibleEnabled(e, true, false);
      return;
    }

    Change change = changeListManager.getChange(virtualFile);
    if (change != null && change.getType() == Change.Type.NEW) {
      setVisibleEnabled(e, true, false);
      return;
    }

    setVisibleEnabled(e, true, true);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (virtualFile == null || project == null || project.isDisposed()) {
      return;
    }

    String urlToOpen = getGithubUrl(project, virtualFile, editor, false);
    if (urlToOpen != null) {
      BrowserUtil.launchBrowser(urlToOpen);
    }
  }

  @Nullable
  public static String getGithubUrl(@NotNull Project project, @NotNull VirtualFile virtualFile, @Nullable Editor editor, boolean quiet) {

    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    final GitRepository repository = manager.getRepositoryForFile(virtualFile);
    if (repository == null) {
      if (!quiet) {
        StringBuilder details = new StringBuilder("file: " + virtualFile.getPresentableUrl() + "; Git repositories: ");
        for (GitRepository repo : manager.getRepositories()) {
          details.append(repo.getPresentableUrl()).append("; ");
        }
        showError(project, "Can't find git repository", details.toString(), quiet);
      }
      return null;
    }

    final String githubRemoteUrl = GithubUtil.findGithubRemoteUrl(repository);
    if (githubRemoteUrl == null) {
      showError(project, "Can't find github remote", null, quiet);
      return null;
    }

    final String rootPath = repository.getRoot().getPath();
    final String path = virtualFile.getPath();
    if (!path.startsWith(rootPath)) {
      showError(project, "File is not under repository root", "Root: " + rootPath + ", file: " + path, quiet);
      return null;
    }

    String branch = getBranchNameOnRemote(project, repository, quiet);
    if (branch == null) {
      return null;
    }

    String relativePath = path.substring(rootPath.length());
    String urlToOpen = makeUrlToOpen(editor, relativePath, branch, githubRemoteUrl);
    if (urlToOpen == null) {
      showError(project, "Can't create properly url", githubRemoteUrl, quiet);
      return null;
    }

    return urlToOpen;
  }

  @Nullable
  private static String makeUrlToOpen(@Nullable Editor editor,
                                      @NotNull String relativePath,
                                      @NotNull String branch,
                                      @NotNull String githubRemoteUrl) {
    final StringBuilder builder = new StringBuilder();
    final String githubRepoUrl = GithubUrlUtil.makeGithubRepoUrlFromRemoteUrl(githubRemoteUrl);
    if (githubRepoUrl == null) {
      return null;
    }
    builder.append(githubRepoUrl).append("/blob/").append(branch).append(relativePath);

    if (editor != null && editor.getDocument().getLineCount() >= 1) {
      // lines are counted internally from 0, but from 1 on github
      SelectionModel selectionModel = editor.getSelectionModel();
      final int begin = editor.getDocument().getLineNumber(selectionModel.getSelectionStart()) + 1;
      final int selectionEnd = selectionModel.getSelectionEnd();
      int end = editor.getDocument().getLineNumber(selectionEnd) + 1;
      if (editor.getDocument().getLineStartOffset(end - 1) == selectionEnd) {
        end -= 1;
      }
      builder.append("#L").append(begin).append('-').append(end);
    }

    return builder.toString();
  }

  @Nullable
  public static String getBranchNameOnRemote(@NotNull Project project, @NotNull GitRepository repository, boolean quiet) {
    GitLocalBranch currentBranch = repository.getCurrentBranch();
    if (currentBranch == null) {
      showError(project, "Can't open the file on GitHub when repository is on detached HEAD. Please checkout a branch.", null, quiet);
      return null;
    }

    GitRemoteBranch tracked = currentBranch.findTrackedBranch(repository);
    if (tracked == null) {
      showError(project, "Can't open the file on GitHub when current branch doesn't have a tracked branch.",
                "Current branch: " + currentBranch + ", tracked info: " + repository.getBranchTrackInfos(), quiet);
      return null;
    }

    return tracked.getNameForRemoteOperations();
  }

  private static void showError(@NotNull Project project, @NotNull String message, @Nullable String details, boolean quiet) {
    if (!quiet) {
      if (details == null) {
        GithubNotifications.showError(project, CANNOT_OPEN_IN_BROWSER, message);
      }
      else {
        GithubNotifications.showError(project, CANNOT_OPEN_IN_BROWSER, message, details);
      }
    }
  }
}