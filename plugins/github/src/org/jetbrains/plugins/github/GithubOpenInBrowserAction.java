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
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import static org.jetbrains.plugins.github.GithubUtil.*;

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
    super("Open in browser", "Open corresponding GitHub link in browser", GithubIcons.Github_icon);
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

    // Check that given repository is properly configured git repository
    if (!isRepositoryOnGitHub(gitRepository)) {
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

  @SuppressWarnings("ConstantConditions")
  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    while (!checkCredentials(project)) {
      final GithubLoginDialog dialog = new GithubLoginDialog(project);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
    }

    final VirtualFile root = project.getBaseDir();
    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    if (manager == null) {
      return;
    }
    final GitRepository gitRepository = manager.getRepositoryForFile(root);
    // Check that given repository is properly configured git repository
    final String githubRemoteUrl = findGithubRemoteUrl(gitRepository);

    final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    final String rootPath = root.getPath();
    final String path = virtualFile.getPath();
    if (!path.startsWith(rootPath)) {
      Messages.showErrorDialog(project, "File is not under project root", CANNOT_OPEN_IN_BROWSER);
      return;
    }

    String branch = getBranchNameOnRemote(project, root);
    if (branch == null) {
      return;
    }

    String relativePath = path.substring(rootPath.length());
    String urlToOpen = makeUrlToOpen(e, relativePath, branch, githubRemoteUrl);
    BrowserUtil.launchBrowser(urlToOpen);
  }

  private static String makeUrlToOpen(@NotNull AnActionEvent e, @NotNull String relativePath, @NotNull String branch,
                                      @NotNull String githubRemoteUrl) {
    final StringBuilder builder = new StringBuilder();
    builder.append(makeGithubRepoUrlFromRemoteUrl(githubRemoteUrl)).append("/blob/").append(branch).append(relativePath);
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    if (editor != null && editor.getDocument().getLineCount() >= 1) {
      final int line = editor.getCaretModel().getLogicalPosition().line + 1; // lines are counted internally from 0, but from 1 on github
      builder.append("#L").append(line);
    }
    return builder.toString();
  }

  @Nullable
  public static String getBranchNameOnRemote(@NotNull Project project, @NotNull VirtualFile root) {
    final GitBranch tracked;
    try {
      final GitBranch current = GitBranchUtil.getCurrentBranch(project, root);
      if (current == null) {
        Messages.showErrorDialog(project, "Cannot find local branch", CANNOT_OPEN_IN_BROWSER);
        return null;
      }
      tracked = GitBranchUtil.tracked(project, root, current.getName());
      if (tracked == null || !tracked.isRemote()) {
        Messages.showErrorDialog(project, "Cannot find tracked branch for branch: " + current.getFullName(), CANNOT_OPEN_IN_BROWSER);
        return null;
      }
    }
    catch (VcsException e1) {
      Messages.showErrorDialog(project, "Error occurred while inspecting branches: " + e1, CANNOT_OPEN_IN_BROWSER);
      return null;
    }
    String branch = tracked.getName();
    if (branch.startsWith("origin/")) {
      branch = branch.substring(7);
    }
    return branch;
  }

}
