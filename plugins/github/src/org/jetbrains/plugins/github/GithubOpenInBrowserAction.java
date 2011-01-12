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
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRemote;
import git4idea.GitUtil;

import javax.swing.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date 12/10/10
 */
public class GithubOpenInBrowserAction extends DumbAwareAction {
  public static final Icon ICON = IconLoader.getIcon("/icons/github.png");
  private static final String CANNOT_OPEN_IN_BROWSER = "Cannot open in browser";

  protected GithubOpenInBrowserAction() {
    super("Open in browser", "Open corresponding GitHub link in browser", ICON);
  }

  @Override
  public void update(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (GithubUtil.areCredentialsEmpty() ||
        project == null || project.isDefault() || virtualFile == null ||
        GithubUtil.getGithubBoundRepository(project) == null) {
      e.getPresentation().setVisible(false);
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (!GithubUtil.checkCredentials(project)){
      Messages.showErrorDialog(project, "Cannot login with GitHub credentials. Please configure them in File | Settings | GitHub", CANNOT_OPEN_IN_BROWSER);
      return;
    }

    final VirtualFile root = project.getBaseDir();
    // Check if git is already initialized and presence of remote branch
    final boolean gitDetected = GitUtil.isUnderGit(root);
    if (!gitDetected) {
      Messages.showErrorDialog(project, "Cannot find any git repository configured for the project", CANNOT_OPEN_IN_BROWSER);
      return;
    }
    final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    final String rootPath = root.getPath();
    final String path = virtualFile.getPath();
    if (!path.startsWith(rootPath)){
      Messages.showErrorDialog(project, "File is not under project root", CANNOT_OPEN_IN_BROWSER);
      return;
    }


    try {
      // Check that given repository is properly configured git repository
      GitRemote githubRemote = null;
      final List<GitRemote> gitRemotes = GitRemote.list(project, root);
      if (gitRemotes.isEmpty()) {
        Messages.showErrorDialog(project, "Git repository doesn't have any remotes configured", CANNOT_OPEN_IN_BROWSER);
        return;
      }
      for (GitRemote gitRemote : gitRemotes) {
        if (gitRemote.pushUrl().contains("git@github.com")) {
          githubRemote = gitRemote;
          break;
        }
      }
      if (githubRemote == null) {
        Messages.showErrorDialog(project, "Configured own github repository is not found", CANNOT_OPEN_IN_BROWSER);
        return;
      }

      final String pushUrl = githubRemote.pushUrl();
      final String login = GithubSettings.getInstance().getLogin();
      final int index = pushUrl.lastIndexOf(login);
      if (index == -1) {
        Messages.showErrorDialog(project, "Github remote repository doesn't seem to be your own repository: " + pushUrl,
                                 CANNOT_OPEN_IN_BROWSER);
        return;
      }
      String repoName = pushUrl.substring(index + login.length() + 1);
      if (repoName.endsWith(".git")) {
        repoName = repoName.substring(0, repoName.length() - 4);
      }
      final RepositoryInfo repositoryInfo = GithubUtil.getDetailedRepositoryInfo(project, repoName);
      if (repositoryInfo == null) {
        Messages
          .showErrorDialog(project, "Github repository doesn't seem to be your own repository: " + pushUrl, CANNOT_OPEN_IN_BROWSER);
        return;
      }
      // TODO[oleg] support custom branches here
      BrowserUtil.launchBrowser("https://github.com/" + login + "/" + repoName + "/blob/master" + path.substring(rootPath.length()));

    } catch (VcsException e1){
      Messages.showErrorDialog(project, "Error happened during git operation: " + e1.getMessage(), CANNOT_OPEN_IN_BROWSER);
      return;
    }

  }
}
