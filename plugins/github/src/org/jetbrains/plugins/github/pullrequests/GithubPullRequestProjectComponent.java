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

import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.dvcs.repo.VcsRepositoryMappingListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.update.UiNotifyConnector;
import git4idea.repo.GitRepository;
import icons.GithubIcons;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.util.GithubUtil;

public class GithubPullRequestProjectComponent extends AbstractProjectComponent implements VcsRepositoryMappingListener {
  public static final String ID = "Pull Requests";
  @Nullable private GithubToolWindow myToolWindow = null;

  public GithubPullRequestProjectComponent(Project project, VcsRepositoryManager repositoryManager) {
    super(project);
  }

  public static GithubPullRequestProjectComponent getInstance(@NotNull Project project) {
    return project.getComponent(GithubPullRequestProjectComponent.class);
  }

  @Nullable
  public GithubToolWindow getToolWindow() {
    return myToolWindow;
  }

  @CalledInAwt
  private void updateToolWindow() {
    // FIXME: update on github credentials change
    ToolWindowManager twm = ToolWindowManager.getInstance(myProject);

    Pair<GitRepository, GithubFullPath> gitRepository = GithubUtil.findGitRepository(myProject);
    if (gitRepository == null) {
      twm.unregisterToolWindow(ID);
      if (myToolWindow != null) {
        Disposer.dispose(myToolWindow);
        myToolWindow = null;
      }
      return;
    }

    if (myToolWindow != null &&
        myToolWindow.getFullPath().equals(gitRepository.second)) {
      return;
    }


    if (myToolWindow != null) {
      Disposer.dispose(myToolWindow);
      myToolWindow = null;
    }

    ToolWindow window = twm.registerToolWindow(ID, true, ToolWindowAnchor.BOTTOM, myProject, true, false);
    window.setIcon(GithubIcons.Github_icon);

    ContentManager contentManager = window.getContentManager();
    contentManager.removeAllContents(true);

    AsyncProcessIcon.Big loadingIcon = new AsyncProcessIcon.Big(getClass().toString());
    Content content = ContentFactory.SERVICE.getInstance().createContent(loadingIcon, "Search", false);
    contentManager.addContent(content);

    myToolWindow = new GithubToolWindow(myProject, gitRepository.first, gitRepository.second);

    UiNotifyConnector.doWhenFirstShown(loadingIcon, () -> ApplicationManager.getApplication().invokeLater(() -> {
      window.getActivation().doWhenDone(() -> {
        myToolWindow.init();
      });
    }));
  }

  @Override
  public void mappingChanged() {
    ApplicationManager.getApplication().invokeLater(() -> updateToolWindow(), myProject.getDisposed());
  }

  @Override
  public void initComponent() {
    ApplicationManager.getApplication().invokeLater(() -> updateToolWindow(), myProject.getDisposed());
    myProject.getMessageBus().connect().subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, this);
  }
}
