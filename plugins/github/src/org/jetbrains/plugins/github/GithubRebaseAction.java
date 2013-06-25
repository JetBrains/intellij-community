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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.actions.BasicAction;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitFetchResult;
import git4idea.update.GitFetcher;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.jetbrains.plugins.github.GithubUtil.setVisibleEnabled;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date 12/8/10
 */
public class GithubRebaseAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(GithubRebaseAction.class.getName());
  private static final String CANNOT_PERFORM_GITHUB_REBASE = "Cannot perform github rebase";

  public GithubRebaseAction() {
    super("Rebase my GitHub fork", "Rebase your GitHub forked repository relative to the origin", GithubIcons.Github_icon);
  }

  public void update(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    if (StringUtil.isEmptyOrSpaces(GithubSettings.getInstance().getLogin()) ||
        project == null || project.isDefault()) {
      setVisibleEnabled(e, false, false);
      return;
    }

    final GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    final GitRepository gitRepository = manager.getRepositoryForFile(project.getBaseDir());
    if (gitRepository == null){
      setVisibleEnabled(e, false, false);
      return;
    }

    // Check that given repository is properly configured git repository
    final GitRemote gitHubRemoteBranch = GithubUtil.findGitHubRemoteBranch(gitRepository);
    if (gitHubRemoteBranch == null) {
      setVisibleEnabled(e, false, false);
      return;
    }

    setVisibleEnabled(e, true, true);
  }

  // TODO: ??? Git preparations -> Modal thread
  @SuppressWarnings("ConstantConditions")
  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final GithubAuthData auth = GithubUtil.getAuthData();

    final VirtualFile root = project.getBaseDir();
    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    if (manager == null) {
      LOG.info("No GitRepositoryManager instance available. Action cancelled.");
      return;
    }
    final GitRepository gitRepository = manager.getRepositoryForFile(project.getBaseDir());
    // Check that given repository is properly configured git repository
    final GitRemote remote = GithubUtil.findGitHubRemoteBranch(gitRepository);
    final String pushUrl = GithubUtil.getGithubUrl(remote);

    final String login = auth.getLogin();
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

    final AtomicReference<String> remoteForForkParentRepo = new AtomicReference<String>();
    final AtomicReference<String> parentRepoUrlRef = new AtomicReference<String>();
    final String finalRepoName = repoName;
    ProgressManager.getInstance().run(new Task.Modal(project, "Access to GitHub", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          // load repository info (network)
          final RepositoryInfo repositoryInfo =
            GithubUtil.runAndValidateAuth(project, auth, indicator, new ThrowableComputable<RepositoryInfo, IOException>() {
              @Override
              public RepositoryInfo compute() throws IOException {
                return GithubUtil.getDetailedRepoInfo(auth, login, finalRepoName);
              }
            });
          if (repositoryInfo == null) {
            Messages.showErrorDialog(project, "Github repository doesn't seem to be your own repository: " + pushUrl,
                                     CANNOT_PERFORM_GITHUB_REBASE);
            return;
          }

          if (!repositoryInfo.isFork()) {
            Messages
              .showErrorDialog(project, "Github repository '" + finalRepoName + "' is not a forked one", CANNOT_PERFORM_GITHUB_REBASE);
            return;
          }

          final String parent = repositoryInfo.getParentName();
          LOG.assertTrue(parent != null, "Parent repository not found!");
          final String parentDotGit = parent + ".git";
          final String parentRepoUrl = GithubApiUtil.getGitHost() + "/" + parentDotGit;
          parentRepoUrlRef.set(parentRepoUrl);

          // Check that corresponding remote branch is configured for the fork origin repo
          out:
          for (GitRemote gitRemote : gitRepository.getRemotes()) {
            for (String url : gitRemote.getUrls()) {
              if (isParentUrl(url, parentDotGit)) {
                remoteForForkParentRepo.set(gitRemote.getName());
                break out;
              }
            }
          }
        }
        catch (IOException e) {
          LOG.info(e);
          GithubUtil.notifyError(project, "Couldn't get information about the repository", GithubUtil.getErrorTextFromException(e));
        }
      }
    });

    final String parentRepoUrl = parentRepoUrlRef.get();
    if (remoteForForkParentRepo.get() == null) {
      final int result = Messages.showYesNoDialog(project, "It is necessary to have '" +
                                                           parentRepoUrl +
                                                           "' as a configured remote. Add remote?", "Github Rebase",
                                                  Messages.getQuestionIcon());
      if (result != Messages.OK){
        return;
      }
    }

    BasicAction.saveAll();

    new Task.Backgroundable(project, "Adding GitHub parent as a remote host") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (remoteForForkParentRepo.get() == null) {
          try {
            LOG.info("Adding GitHub parent as a remote host");
            indicator.setText("Adding GitHub parent as a remote host");
            final GitSimpleHandler addRemoteHandler = new GitSimpleHandler(project, root, GitCommand.REMOTE);
            addRemoteHandler.setSilent(true);

            remoteForForkParentRepo.set("upstream");
            addRemoteHandler.addParameters("add", remoteForForkParentRepo.get(), parentRepoUrl);
            addRemoteHandler.run();
            if (addRemoteHandler.getExitCode() != 0) {
              showErrorMessage(project, "Failed to add GitHub remote: '" + parentRepoUrl + "'", indicator);
            }

            // catch newly added remote
            gitRepository.update();
          }
          catch (VcsException e1) {
            final String message = "Error happened during git operation: " + e1.getMessage();
            showErrorMessage(project, message, indicator);
          }
        }

        if (!fetchParentOrNotifyError(project, gitRepository, remoteForForkParentRepo.get(), indicator)) {
          return;
        }

        final GithubRebase action = (GithubRebase)ActionManager.getInstance().getAction("Github.Rebase.Internal");
        action.setRebaseOrigin(remoteForForkParentRepo.get());
        final AnActionEvent actionEvent =
          new AnActionEvent(e.getInputEvent(), e.getDataContext(), e.getPlace(), e.getPresentation(), e.getActionManager(),
                            e.getModifiers());
        ApplicationManager.getApplication().invokeAndWait(new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                action.actionPerformed(actionEvent);
              }
            });
          }
        }, indicator.getModalityState());
      }
    }.queue();
  }

  private static boolean isParentUrl(@NotNull String url, @NotNull String parentDotGit) {
    // the separator is checked because we may have a repository which ends with "parentDotGit", but is not a parent,
    // e.g. "my_other_repository_parent.git"
    return url.endsWith("/" + parentDotGit)     // http or git
           || url.endsWith(":" + parentDotGit); // ssh
  }

  private static boolean fetchParentOrNotifyError(@NotNull final Project project, @NotNull final GitRepository repository,
                                                  @NotNull final String remote,
                                                  @NotNull final ProgressIndicator indicator) {
    GitFetchResult result = new GitFetcher(project, indicator, false).fetch(repository.getRoot(), remote);
    if (!result.isSuccess()) {
      GitFetcher.displayFetchResult(project, result, null, result.getErrors());
      return false;
    }
    return true;
  }

  private static void showErrorMessage(final Project project, final String message, ProgressIndicator indicator) {
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(project, message, CANNOT_PERFORM_GITHUB_REBASE);
      }
    }, indicator.getModalityState());
  }
}
