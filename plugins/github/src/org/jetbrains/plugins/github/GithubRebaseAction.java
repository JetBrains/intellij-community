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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
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
    super("Rebase my GitHub fork", "Rebase your GitHub forked repository relative to the origin", ICON);
  }

  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (StringUtil.isEmptyOrSpaces(GithubSettings.getInstance().getLogin()) ||
        project == null || project.isDefault() ||
        GithubUtil.getGithubBoundRepository(project) == null) {
      e.getPresentation().setEnabled(false);
      e.getPresentation().setVisible(false);
      return;
    }
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(true);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (!GithubUtil.checkCredentials(project)){
      Messages.showErrorDialog(project, "Cannot login with GitHub credentials. Please configure them in File | Settings | GitHub", CANNOT_PERFORM_GITHUB_REBASE);
      return;
    }

    final VirtualFile root = project.getBaseDir();
    // Check if git is already initialized and presence of remote branch
    final boolean gitDetected = GitUtil.isUnderGit(root);
    if (!gitDetected) {
      Messages.showErrorDialog(project, "Cannot find any git repository configured for the project", CANNOT_PERFORM_GITHUB_REBASE);
      return;
    }

    try {
      // Check that given repository is properly configured git repository
      final GitRemote githubRemote = GithubUtil.accessToGithubWithModalProgress(project, new Computable<GitRemote>(){
        @Override
        public GitRemote compute() {
          ProgressManager.getInstance().getProgressIndicator().setText("Reading branches information");
          return GithubUtil.getGithubBoundRepository(project);
        }
      });
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

      final RepositoryInfo repositoryInfo = GithubUtil.getDetailedRepositoryInfo(project, login, repoName);
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
        final int result = Messages.showYesNoDialog(project, "It is necessary to have '" +
                                                             parentRepoUrl +
                                                             "' as a configured remote. Add remote?", "Github Rebase",
                                                    Messages.getQuestionIcon());
        if (result != Messages.OK){
          return;
        }

        GithubUtil.accessToGithubWithModalProgress(project, new Runnable() {
          public void run() {
            try {
              LOG.info("Adding GitHub parent as a remote host");
              ProgressManager.getInstance().getProgressIndicator().setText("Adding GitHub parent as a remote host");
              final GitSimpleHandler addRemoteHandler = new GitSimpleHandler(project, root, GitCommand.REMOTE);
              addRemoteHandler.setNoSSH(true);
              addRemoteHandler.setSilent(true);
              final String remoteName = parent.substring(0, parent.lastIndexOf('/'));
              addRemoteHandler.addParameters("add", remoteName, parentRepoUrl);
              addRemoteHandler.run();
              if (addRemoteHandler.getExitCode() != 0) {
                showErrorMessageInEDT(project, "Failed to add GitHub remote: '" + parentRepoUrl + "'");
                return;
              }

              LOG.info("Updating remotes");
              ProgressManager.getInstance().getProgressIndicator().setText("Updating remotes");
              final GitSimpleHandler updateRemotesHandler = new GitSimpleHandler(project, root, GitCommand.REMOTE);
              updateRemotesHandler.setNoSSH(true);
              updateRemotesHandler.setSilent(true);
              updateRemotesHandler.addParameters("update");
              updateRemotesHandler.run();
              if (updateRemotesHandler.getExitCode() != 0) {
                showErrorMessageInEDT(project, "Failed to update remotes");
                return;
              }
            }
            catch (VcsException e1) {
              final String message = "Error happened during git operation: " + e1.getMessage();
              showErrorMessageInEDT(project, message);
              return;
            }
          }
        });
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

  private void showErrorMessageInEDT(final Project project, final String message) {
    UIUtil.invokeLaterIfNeeded(new Runnable(){
      @Override
      public void run() {
        Messages.showErrorDialog(project, message, CANNOT_PERFORM_GITHUB_REBASE);
      }
    });
  }
}
