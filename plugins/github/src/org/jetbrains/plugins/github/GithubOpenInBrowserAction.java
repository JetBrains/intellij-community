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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date 12/10/10
 */
public class GithubOpenInBrowserAction extends DumbAwareAction {
  public static final String CANNOT_OPEN_IN_BROWSER = "Cannot open in browser";
  private static final Logger LOG = Logger.getInstance(GithubOpenInBrowserAction.class.getName());

  protected GithubOpenInBrowserAction() {
    super("Open in browser", "Open corresponding GitHub link in browser", GithubUtil.GITHUB_ICON);
  }

  @Override
  public void update(final AnActionEvent e) {
    final boolean applicable = isApplicable(e);
    e.getPresentation().setVisible(applicable);
    e.getPresentation().setEnabled(applicable);
  }

  private boolean isApplicable(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (project == null || project.isDefault() || virtualFile == null) {
      return false;
    }

    final VirtualFile dir = project.getBaseDir();
    if (dir == null) {
      return false;
    }

    final GitRepository gitRepository = GitRepositoryManager.getInstance(project).getRepositoryForFile(dir);
    if (gitRepository == null) {
      return false;
    }

    // Check that given repository is properly configured git repository
    final GitRemote gitHubRemoteBranch = GithubUtil.findGitHubRemoteBranch(gitRepository);
    if (gitHubRemoteBranch == null) {
      return false;
    }
    return true;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    while (!GithubUtil.checkCredentials(project)) {
      final GithubLoginDialog dialog = new GithubLoginDialog(project);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
    }

    final VirtualFile root = project.getBaseDir();
    final GitRepository gitRepository = GitRepositoryManager.getInstance(project).getRepositoryForFile(root);
    // Check that given repository is properly configured git repository
    final GitRemote gitRemote = GithubUtil.findGitHubRemoteBranch(gitRepository);
    final String pushUrl = GithubUtil.getGithubUrl(gitRemote);

    final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    final String rootPath = root.getPath();
    final String path = virtualFile.getPath();
    if (!path.startsWith(rootPath)) {
      Messages.showErrorDialog(project, "File is not under project root", CANNOT_OPEN_IN_BROWSER);
      return;
    }

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
    }
    else {
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
      if (current == null) {
        Messages.showErrorDialog(project, "Cannot find local branch", CANNOT_OPEN_IN_BROWSER);
        return;
      }
      tracked = current.tracked(project, root);
      if (tracked == null || !tracked.isRemote()) {
        Messages.showErrorDialog(project, "Cannot find tracked branch for branch: " + current.getFullName(), CANNOT_OPEN_IN_BROWSER);
        return;
      }
    }
    catch (VcsException e1) {
      Messages.showErrorDialog(project, "Error occurred while inspecting branches: " + e1, CANNOT_OPEN_IN_BROWSER);
      return;
    }
    String branch = tracked.getName();
    if (branch.startsWith("origin/")) {
      branch = branch.substring(7);
    }

    final StringBuilder builder = new StringBuilder();
    builder.append("https://github.com/").append(repoInfo).append("/blob/").append(branch).append(path.substring(rootPath.length()));
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (editor != null) {
      final int line = editor.getCaretModel().getLogicalPosition().line;
      builder.append("#L").append(line);
    }
    BrowserUtil.launchBrowser(builder.toString());
  }
}
