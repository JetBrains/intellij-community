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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitRemote;
import git4idea.GitUtil;
import git4idea.actions.BasicAction;

import javax.swing.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date 12/8/10
 */
public class GithubRebaseAction extends DumbAwareAction {
  public static final Icon ICON = IconLoader.getIcon("/icons/github.png");
  private static final Logger LOG = Logger.getInstance(GithubRebaseAction.class.getName());

  public GithubRebaseAction() {
    super("Rebase my fork", "Rebase your forked repository relative to the origin", ICON);
  }

  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null || project.isDefault()){
      e.getPresentation().setEnabled(false);
      return;
    }
    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
    if (roots.length == 0){
      Messages.showErrorDialog(project, "Project doesn't have any project roots", "Cannot create new GitHub repository");
      return;
    }
    final VirtualFile root = roots[0];
    // Check if git is already initialized and presence of remote branch
    final boolean gitDetected = GitUtil.isUnderGit(root);
    if (!gitDetected) {
      Messages.showErrorDialog(project, "Cannot find any git repository configured for the project", "Cannot perform github rebase");
      return;
    }

    // Check current branch
    try {
      final GitBranch currentBranch = GitBranch.current(project, root);
      if (currentBranch == null){

        Messages.showErrorDialog(project, "Cannot find current branch", "Cannot perform github rebase");
        return;
      }
      if (!"master".equals(currentBranch.getName())){
        Messages.showErrorDialog(project, "Cannot perform rebase with '" + currentBranch.getName() + "' branch.\nPlease switch to master", "Cannot perform github rebase");
        return;
      }
    }
    catch (VcsException e1) {
      Messages.showErrorDialog(project, "Error happened during git operation: " + e1.getMessage(), "Cannot perform github rebase");
      return;
    }

    // Check that given repository is properly configured git repository
    GitRemote githubRemote = null;
    try {
      final List<GitRemote> gitRemotes = GitRemote.list(project, root);
      if (gitRemotes.isEmpty()) {
        Messages.showErrorDialog(project, "Git repository doesn't have any remotes configured", "Cannot perform github rebase");
        return;
      }
      for (GitRemote gitRemote : gitRemotes) {
        if (gitRemote.pushUrl().contains("git@github.com")){
          githubRemote = gitRemote;
          break;
        }
      }
      if (githubRemote == null) {
        Messages.showErrorDialog(project, "Configured own github repository is not found", "Cannot perform github rebase");
        return;
      }
    }
    catch (VcsException e2) {
      Messages.showErrorDialog(project, "Error happened during git operation: " + e2.getMessage(), "Cannot perform github rebase");
      return;
    }

    final String pushUrl = githubRemote.pushUrl();
    final String login = GithubSettings.getInstance().getLogin();
    final int index = pushUrl.lastIndexOf(login);
    if (index == -1){
      Messages.showErrorDialog(project, "Github remote repository doesn't seem to be your own repository: " + pushUrl, "Cannot perform github rebase");
      return;
    }
    String repoName = pushUrl.substring(index + login.length() + 1);
    if (repoName.endsWith(".git")){
      repoName = repoName.substring(0, repoName.length() - 4);
    }

    final RepositoryInfo repositoryInfo = GithubUtil.getDetailedRepositoryInfo(project, repoName);
    if (repositoryInfo == null){
      Messages.showErrorDialog(project, "Github repository doesn't seem to be your own repository: " + pushUrl, "Cannot perform github rebase");
      return;
    }

    if (!repositoryInfo.isFork()){
      Messages.showErrorDialog(project, "Github repository '" + repoName + "' is not a forked one", "Cannot perform github rebase");
      return;
    }

    final String parent = repositoryInfo.getParent();
    LOG.assertTrue(parent != null, "Parent repository not found!");
    final String parentRepoString = "git://github.com/" + parent + ".git";

    BasicAction.saveAll();
    int i = Messages.showYesNoDialog(project, "Perform rebase relative to '" + parentRepoString + "'?", "Github Rebase", Messages.getQuestionIcon());
    if (i != Messages.OK){
      return;
    }
  }
}
