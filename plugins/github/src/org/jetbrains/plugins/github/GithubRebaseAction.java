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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableConsumer;
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
    if (!GithubUtil.isRepositoryOnGitHub(gitRepository)) {
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

    final VirtualFile root = project.getBaseDir();
    GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
    if (manager == null) {
      LOG.info("No GitRepositoryManager instance available. Action cancelled.");
      return;
    }
    final GitRepository gitRepository = manager.getRepositoryForFile(project.getBaseDir());
    // Check that given repository is properly configured git repository
    final String pushUrl = GithubUtil.findGithubRemoteUrl(gitRepository);

    final String login = GithubSettings.getInstance().getLogin();
    final int index = pushUrl.lastIndexOf(login);
    if (index == -1) {
      GithubNotifications
        .showWarningDialog(project, CANNOT_PERFORM_GITHUB_REBASE, "Github remote repository doesn't seem to be your own repository: " + pushUrl);
      return;
    }
    String repoName = pushUrl.substring(index + login.length() + 1);
    if (repoName.endsWith(".git")) {
      repoName = repoName.substring(0, repoName.length() - 4);
    }

    final Ref<String> remoteForForkParentRepo = new Ref<String>();
    final Ref<String> parentRepoUrlRef = new Ref<String>();
    final String finalRepoName = repoName;
    ProgressManager.getInstance().run(new Task.Modal(project, "Access to GitHub", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          // load repository info (network)
          final Ref<RepositoryInfo> repositoryInfoRef = new Ref<RepositoryInfo>();
          GithubUtil.runAndGetValidAuth(project, indicator, new ThrowableConsumer<GithubAuthData, IOException>() {
            @Override
            public void consume(GithubAuthData authData) throws IOException {
              repositoryInfoRef.set(GithubUtil.getDetailedRepoInfo(authData, login, finalRepoName));
            }
          });
          if (repositoryInfoRef.isNull()) {
            GithubNotifications
              .showWarning(project, CANNOT_PERFORM_GITHUB_REBASE, "Github repository doesn't seem to be your own repository: " + pushUrl);
            return;
          }

          if (!repositoryInfoRef.get().isFork()) {
            GithubNotifications
              .showWarning(project, CANNOT_PERFORM_GITHUB_REBASE, "Github repository '" + finalRepoName + "' is not a forked one");
            return;
          }

          final String parent = repositoryInfoRef.get().getParentName();
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
          GithubNotifications.showError(project, "Couldn't get information about the repository", e);
        }
      }
    });

    final String parentRepoUrl = parentRepoUrlRef.get();
    if (remoteForForkParentRepo.isNull()) {
      final int result = GithubNotifications.showYesNoDialog(project, "Github Rebase", "It is necessary to have '" +
                                                                                       parentRepoUrl +
                                                                                       "' as a configured remote. Add remote?");
      if (result != Messages.OK){
        return;
      }
    }

    BasicAction.saveAll();

    new Task.Backgroundable(project, "Rebase GitHub fork") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        if (remoteForForkParentRepo.isNull()) {
          try {
            LOG.info("Adding GitHub parent as a remote host");
            indicator.setText("Adding GitHub parent as a remote host");
            final GitSimpleHandler addRemoteHandler = new GitSimpleHandler(project, root, GitCommand.REMOTE);
            addRemoteHandler.setSilent(true);

            remoteForForkParentRepo.set("upstream");
            addRemoteHandler.addParameters("add", remoteForForkParentRepo.get(), parentRepoUrl);
            addRemoteHandler.run();
            if (addRemoteHandler.getExitCode() != 0) {
              GithubNotifications.showError(project, CANNOT_PERFORM_GITHUB_REBASE, "Failed to add GitHub remote: '" + parentRepoUrl + "'");
              return;
            }

            // catch newly added remote
            gitRepository.update();
          }
          catch (VcsException e) {
            GithubNotifications.showError(project, CANNOT_PERFORM_GITHUB_REBASE, e);
            return;
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
            action.actionPerformed(actionEvent);
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
}
