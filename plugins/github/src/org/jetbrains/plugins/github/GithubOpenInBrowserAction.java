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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitRemote;
import git4idea.GitUtil;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date 12/10/10
 */
public class GithubOpenInBrowserAction extends DumbAwareAction {
  public static final Icon ICON = IconLoader.getIcon("/icons/github.png");
  private static final String CANNOT_OPEN_IN_BROWSER = "Cannot open in browser";
  private static final Logger LOG = Logger.getInstance(GithubOpenInBrowserAction.class.getName());

  protected GithubOpenInBrowserAction() {
    super("Open in browser", "Open corresponding GitHub link in browser", ICON);
  }

  @Override
  public void update(final AnActionEvent e) {
    final long startTime = System.nanoTime();
    try {
      final Project project = e.getData(PlatformDataKeys.PROJECT);
      final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
      if (StringUtil.isEmptyOrSpaces(GithubSettings.getInstance().getLogin()) ||
          project == null || project.isDefault() || virtualFile == null ||
          GithubUtil.getGithubBoundRepository(project) == null) {
        e.getPresentation().setVisible(false);
        e.getPresentation().setEnabled(false);
        return;
      }
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(true);
    }
    finally {
      if (LOG.isDebugEnabled()) {
        LOG.debug("GithubOpenInBrowserAction#update finished in: " + (System.nanoTime() - startTime) / 10e6 + "ms");
      }
    }
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


    // Check that given repository is properly configured git repository
    final GitRemote githubRemote = GithubUtil.getGithubBoundRepository(project);
    if (githubRemote == null) {
      Messages.showErrorDialog(project, "Configured github repository is not found", CANNOT_OPEN_IN_BROWSER);
      return;
    }

    final String pushUrl = githubRemote.pushUrl();
    int index = -1;
    if (pushUrl.startsWith(GithubUtil.getHttpsUrl())) {
      index = pushUrl.lastIndexOf('/');
      if (index == -1) {
        Messages.showErrorDialog(project, "Cannot extract info about repository name: " + pushUrl, CANNOT_OPEN_IN_BROWSER);
        return;
      }
      index = pushUrl.substring(0, index).lastIndexOf('/');
      if (index == -1) {
        Messages.showErrorDialog(project, "Cannot extract info about repository owner: " + pushUrl, CANNOT_OPEN_IN_BROWSER);
        return;
      }
    } else {
      index = pushUrl.lastIndexOf(':');
      if (index == -1) {
        Messages.showErrorDialog(project, "Cannot extract info about repository name and owner: " + pushUrl, CANNOT_OPEN_IN_BROWSER);
        return;
      }
    }
    String repoInfo = pushUrl.substring(index + 1);
    if (repoInfo.endsWith(".git")) {
      repoInfo = repoInfo.substring(0, repoInfo.length() - 4);
    }

    // Get current tracked branch
    final GitBranch tracked;
    try {
      final GitBranch current = GitBranch.current(project, root);
      if (current == null){
        Messages.showErrorDialog(project, "Cannot find local branch", CANNOT_OPEN_IN_BROWSER);
        return;
      }
      tracked = current.tracked(project, root);
      if (tracked == null || !tracked.isRemote()){
        Messages.showErrorDialog(project, "Cannot find tracked branch for branch: " + current.getFullName(), CANNOT_OPEN_IN_BROWSER);
        return;
      }
    }
    catch (VcsException e1) {
      Messages.showErrorDialog(project, "Error occured while inspecting branches: " + e1, CANNOT_OPEN_IN_BROWSER);
      return;
    }
    String branch = tracked.getName();
    if (branch.startsWith("origin/")){
      branch = branch.substring(7);
    }
    BrowserUtil.launchBrowser("https://github.com/" + repoInfo + "/blob/" + branch + path.substring(rootPath.length()));
  }
}
