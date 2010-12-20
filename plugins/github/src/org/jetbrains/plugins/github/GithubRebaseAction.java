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

import com.intellij.openapi.actionSystem.ActionManager;
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
import git4idea.GitRemote;
import git4idea.GitUtil;
import git4idea.actions.BasicAction;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;

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
  private static final String CANNOT_PERFORM_GITHUB_REBASE = "Cannot perform github rebase";

  public GithubRebaseAction() {
    super("Rebase my fork", "Rebase your forked repository relative to the origin", ICON);
  }

  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null || project.isDefault()) {
      e.getPresentation().setVisible(false);
      return;
    }
    if (GithubUtil.getGithubBoundRepository(project) == null){
      e.getPresentation().setVisible(false);
      return;
    }
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
    if (roots.length == 0) {
      Messages.showErrorDialog(project, "Project doesn't have any project roots", CANNOT_PERFORM_GITHUB_REBASE);
      return;
    }
    final VirtualFile root = roots[0];
    // Check if git is already initialized and presence of remote branch
    final boolean gitDetected = GitUtil.isUnderGit(root);
    if (!gitDetected) {
      Messages.showErrorDialog(project, "Cannot find any git repository configured for the project", CANNOT_PERFORM_GITHUB_REBASE);
      return;
    }

    try {
      // Check that given repository is properly configured git repository
      final GitRemote githubRemote = GithubUtil.getGithubBoundRepository(project);
      final List<GitRemote> gitRemotes = GitRemote.list(project, root);
      LOG.assertTrue(githubRemote != null);

      final String pushUrl = githubRemote.pushUrl();
      final String login = GithubSettings.getInstance().getLogin();
      final int index = pushUrl.lastIndexOf(login);
      if (index == -1) {
        Messages.showErrorDialog(project, "Github remote repository doesn't seem to be your own repository: " + pushUrl,
                                 CANNOT_PERFORM_GITHUB_REBASE);
        return;
      }
      String repoName = pushUrl.substring(index + login.length() + 1);
      if (repoName.endsWith(".git")) {
        repoName = repoName.substring(0, repoName.length() - 4);
      }

      final RepositoryInfo repositoryInfo = GithubUtil.getDetailedRepositoryInfo(project, repoName);
      if (repositoryInfo == null) {
        Messages
          .showErrorDialog(project, "Github repository doesn't seem to be your own repository: " + pushUrl, CANNOT_PERFORM_GITHUB_REBASE);
        return;
      }

      if (!repositoryInfo.isFork()) {
        Messages.showErrorDialog(project, "Github repository '" + repoName + "' is not a forked one", CANNOT_PERFORM_GITHUB_REBASE);
        return;
      }

      final String parent = repositoryInfo.getParent();
      LOG.assertTrue(parent != null, "Parent repository not found!");
      final String parentRepoSuffix = parent + ".git";
      final String parentRepoUrl = "git://github.com/" + parentRepoSuffix;

      // Check that corresponding remote branch is configured for the fork origin repo
      boolean remoteForParentSeen = false;
      for (GitRemote gitRemote : gitRemotes) {
        final String fetchUrl = gitRemote.fetchUrl();
        if (fetchUrl.endsWith(parent + ".git")) {
          remoteForParentSeen = true;
          break;
        }
      }
      if (!remoteForParentSeen){
        final int result = Messages.showYesNoDialog(project, "It is nescessary to have '" +
                                                        parentRepoUrl +
                                                        "' as a configured remote. Add remote?", "Github Rebase",
                                               Messages.getQuestionIcon());
        if (result != Messages.OK){
          return;
        }

        LOG.info("Adding GitHub as a remote host");
        final GitSimpleHandler addRemoteHandler = new GitSimpleHandler(project, root, GitCommand.REMOTE);
        addRemoteHandler.setNoSSH(true);
        addRemoteHandler.setSilent(true);
        addRemoteHandler.addParameters("add", repoName, parentRepoUrl);
        addRemoteHandler.run();
        if (addRemoteHandler.getExitCode() != 0) {
          Messages.showErrorDialog("Failed to add GitHub remote: '" + parentRepoUrl + "'", CANNOT_PERFORM_GITHUB_REBASE);
          return;
        }

        LOG.info("Updating remotes");
        final GitSimpleHandler updateRemotesHandler = new GitSimpleHandler(project, root, GitCommand.REMOTE);
        updateRemotesHandler.setNoSSH(true);
        updateRemotesHandler.setSilent(true);
        updateRemotesHandler.addParameters("update");
        updateRemotesHandler.run();
        if (updateRemotesHandler.getExitCode() != 0) {
          Messages.showErrorDialog("Failed to update remotes", CANNOT_PERFORM_GITHUB_REBASE);
          return;
        }
      }

      BasicAction.saveAll();
      final GithubRebase action = (GithubRebase) ActionManager.getInstance().getAction("Github.Rebase.Internal");
      action.setRebaseOrigin(parent);
      final AnActionEvent actionEvent =
        new AnActionEvent(e.getInputEvent(), e.getDataContext(), e.getPlace(), e.getPresentation(), e.getActionManager(), e.getModifiers());
      action.actionPerformed(actionEvent);
    }
    catch (VcsException e1) {
      Messages.showErrorDialog(project, "Error happened during git operation: " + e1.getMessage(), CANNOT_PERFORM_GITHUB_REBASE);
      return;
    }
  }
}
