/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.pullRequests;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentProvider;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.NotNullFunction;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubConnection;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.api.data.GithubPullRequest;
import org.jetbrains.plugins.github.util.GithubAuthDataHolder;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GithubPullRequestsViewProvider implements ChangesViewContentProvider {
  private final static String CANNOT_LIST_PULL_REQUESTS = "Cannot list pull requests";
  private static final Logger LOG = Logger.getInstance(GithubPullRequestsViewProvider.class);

  @NotNull private final Project myProject;
  private GithubPullRequestExplorer myRequestExplorer;

  public GithubPullRequestsViewProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public JComponent initContent() {
    final GithubAuthDataHolder authHolder = GithubAuthDataHolder.createFromSettings();
    final List<GithubPullRequest> requests = loadPullRequests(myProject, authHolder);      //TODO: create separate loader?
    myRequestExplorer = new GithubPullRequestExplorer(myProject, requests, authHolder);
    return myRequestExplorer;
  }

  @Override
  public void disposeContent() {
    myRequestExplorer.removeAll();
    closePullRequestTabs();

  }

  private void closePullRequestTabs() {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.VCS);

    if (toolWindow != null) {
      //TODO: close all tabbed contents
      //Content content = toolWindow.getContentManager().findContent(tabName);
      //ContentsUtil.closeContentTab(toolWindow.getContentManager(), content);
    }
  }

  private static List<GithubPullRequest> loadPullRequests(@NotNull final Project project, @NotNull final GithubAuthDataHolder authHolder) {
    final List<GithubPullRequest> result = new ArrayList<>();
    if (project.isDisposed() || !GithubUtil.testGitExecutable(project)) {
      return result;
    }

    final GitRepository gitRepository = GithubUtil.getGitRepository(project, project.getBaseDir());
    if (gitRepository == null) {
      GithubNotifications.showError(project, CANNOT_LIST_PULL_REQUESTS, "Can't find git repository");
      return result;
    }

    final String remoteUrl = GithubUtil.findGithubRemoteUrl(gitRepository);
    if (remoteUrl == null) {
      GithubNotifications.showError(project, CANNOT_LIST_PULL_REQUESTS, "Can't find GitHub remote");
      return result;
    }

    final GithubFullPath path = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl);
    if (path == null) {
      GithubNotifications.showError(project, CANNOT_LIST_PULL_REQUESTS, "Can't process remote: " + remoteUrl);
      return result;
    }

    // TODO: Use 'loading' panel instead
    GithubUtil.computeValueInModal(project, "Loading pull requests...", indicator -> {
      try {
        return GithubUtil.runTask(project, authHolder, indicator, connection ->
        {
          final GithubConnection.PagedRequest<GithubPullRequest> requests =
            GithubApiUtil.getPullRequests(path.getUser(), path.getRepository());
          while (requests.hasNext()) {
            final List<GithubPullRequest> pullRequests = requests.next(connection);
            result.addAll(pullRequests);
          }
          return result;
        });
      }
      catch (IOException e1) {
        GithubNotifications.showError(project, CANNOT_LIST_PULL_REQUESTS, e1);
      }
      return result;
    });
    return result;
  }

  public static class GithubPullRequestsVisibilityPredicate implements NotNullFunction<Project, Boolean> {
    @NotNull
    @Override
    public Boolean fun(Project project) {
      final GitRepository gitRepository = GithubUtil.getGitRepository(project, project.getBaseDir());
      return gitRepository != null;
    }
  }
}
