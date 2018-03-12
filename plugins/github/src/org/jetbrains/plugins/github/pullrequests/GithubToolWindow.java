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
package org.jetbrains.plugins.github.pullrequests;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.containers.ContainerUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.api.data.GithubPullRequest;
import org.jetbrains.plugins.github.pullrequests.details.GithubPullRequestDetailsPanel;
import org.jetbrains.plugins.github.pullrequests.overview.GithubPullRequestListPanel;
import org.jetbrains.plugins.github.util.GithubAuthDataHolder;

public class GithubToolWindow implements Disposable {
  public static final String ID = "Pull Requests";

  @NotNull private final Project myProject;
  @NotNull private final GitRepository myGitRepository;
  @NotNull private final GithubFullPath myFullPath;

  @NotNull private final GithubAuthDataHolder myAuthDataHolder;

  private boolean myDisposed;

  public GithubToolWindow(@NotNull Project project, @NotNull GitRepository gitRepository, @NotNull GithubFullPath fullPath) {
    myProject = project;
    myGitRepository = gitRepository;
    myFullPath = fullPath;

    myAuthDataHolder = GithubAuthDataHolder.createFromSettings();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public GithubFullPath getFullPath() {
    return myFullPath;
  }

  @NotNull
  public GithubAuthDataHolder getAuthDataHolder() {
    return myAuthDataHolder;
  }

  @NotNull
  public GitRepository getGitRepository() {
    return myGitRepository;
  }

  @CalledInAwt
  public void init() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myDisposed) return;

    // FIXME: show "set credentials" link instead of modal dialog

    ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(ID);
    ContentManager contentManager = window.getContentManager();
    contentManager.removeAllContents(true);

    GithubPullRequestListPanel panel = new GithubPullRequestListPanel(this);

    Content content = ContentFactory.SERVICE.getInstance().createContent(panel, "Search", false);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(panel.getPreferredFocusedComponent());
    contentManager.addContent(content);
  }

  @CalledInAwt
  public void openPullRequestTab(@NotNull GithubPullRequest request) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myDisposed) return;

    ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(ID);
    ContentManager contentManager = window.getContentManager();

    Content existingDetailsContent = ContainerUtil.find(contentManager.getContents(), (content) ->
      content.getComponent() instanceof GithubPullRequestDetailsPanel &&
      request.getNumber() == ((GithubPullRequestDetailsPanel)content.getComponent()).getNumber()
    );
    if (existingDetailsContent != null) {
      contentManager.setSelectedContent(existingDetailsContent, true);
      return;
    }

    GithubPullRequestDetailsPanel details = new GithubPullRequestDetailsPanel(this, request.getNumber());

    String title = "#" + request.getNumber() + " " + StringUtil.first(request.getTitle(), 30, true);
    Content detailsContent = ContentFactory.SERVICE.getInstance().createContent(details, title, false);
    detailsContent.setPreferredFocusableComponent(details.getPreferredFocusedComponent());
    contentManager.addContent(detailsContent);
    contentManager.setSelectedContent(detailsContent, true);
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }
}
