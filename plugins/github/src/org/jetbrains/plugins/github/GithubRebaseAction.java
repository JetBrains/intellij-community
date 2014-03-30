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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableConvertor;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.actions.BasicAction;
import git4idea.commands.*;
import git4idea.rebase.GitRebaseProblemDetector;
import git4idea.rebase.GitRebaser;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitFetchResult;
import git4idea.update.GitFetcher;
import git4idea.update.GitUpdateResult;
import git4idea.util.GitPreservingProcess;
import icons.GithubIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.api.GithubRepoDetailed;
import org.jetbrains.plugins.github.exceptions.GithubOperationCanceledException;
import org.jetbrains.plugins.github.util.*;

import java.io.IOException;
import java.util.Collections;

import static org.jetbrains.plugins.github.util.GithubUtil.setVisibleEnabled;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date 12/8/10
 */
public class GithubRebaseAction extends DumbAwareAction {
  private static final Logger LOG = GithubUtil.LOG;
  private static final String CANNOT_PERFORM_GITHUB_REBASE = "Can't perform github rebase";

  public GithubRebaseAction() {
    super("Rebase my GitHub fork", "Rebase your GitHub forked repository relative to the origin", GithubIcons.Github_icon);
  }

  public void update(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    if (project == null || project.isDefault()) {
      setVisibleEnabled(e, false, false);
      return;
    }

    final GitRepository gitRepository = GithubUtil.getGitRepository(project, file);
    if (gitRepository == null) {
      setVisibleEnabled(e, false, false);
      return;
    }

    if (!GithubUtil.isRepositoryOnGitHub(gitRepository)) {
      setVisibleEnabled(e, false, false);
      return;
    }

    setVisibleEnabled(e, true, true);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

    if (project == null || project.isDisposed() || !GithubUtil.testGitExecutable(project)) {
      return;
    }

    rebaseMyGithubFork(project, file);
  }

  private static void rebaseMyGithubFork(@NotNull final Project project, @Nullable final VirtualFile file) {
    final GitRepository gitRepository = GithubUtil.getGitRepository(project, file);
    if (gitRepository == null) {
      GithubNotifications.showError(project, CANNOT_PERFORM_GITHUB_REBASE, "Can't find git repository");
      return;
    }
    BasicAction.saveAll();

    new Task.Backgroundable(project, "Rebasing GitHub fork...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        gitRepository.update();
        String upstreamRemoteUrl = GithubUtil.findUpstreamRemote(gitRepository);

        if (upstreamRemoteUrl == null) {
          LOG.info("Configuring upstream remote");
          indicator.setText("Configuring upstream remote...");
          upstreamRemoteUrl = configureUpstreamRemote(project, gitRepository, indicator);
          if (upstreamRemoteUrl == null) {
            return;
          }
        }

        if (!GithubUrlUtil.isGithubUrl(upstreamRemoteUrl)) {
          GithubNotifications
            .showError(project, CANNOT_PERFORM_GITHUB_REBASE, "Configured upstream is not a GitHub repository: " + upstreamRemoteUrl);
          return;
        }
        else {
          final GithubFullPath userAndRepo = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(upstreamRemoteUrl);
          final String login = GithubSettings.getInstance().getLogin();
          if (userAndRepo != null) {
            if (userAndRepo.getUser().equals(login)) {
              GithubNotifications.showError(project, CANNOT_PERFORM_GITHUB_REBASE,
                                            "Configured upstream seems to be your own repository: " + upstreamRemoteUrl);
              return;
            }
          }
        }

        LOG.info("Fetching upstream");
        indicator.setText("Fetching upstream...");
        if (!fetchParent(project, gitRepository, indicator)) {
          return;
        }

        LOG.info("Rebasing current branch");
        indicator.setText("Rebasing current branch...");
        rebaseCurrentBranch(project, gitRepository, indicator);
      }
    }.queue();
  }

  @Nullable
  static String configureUpstreamRemote(@NotNull Project project,
                                        @NotNull GitRepository gitRepository,
                                        @NotNull ProgressIndicator indicator) {
    GithubRepoDetailed repositoryInfo = loadRepositoryInfo(project, gitRepository, indicator);
    if (repositoryInfo == null) {
      return null;
    }

    if (!repositoryInfo.isFork() || repositoryInfo.getParent() == null) {
      GithubNotifications.showWarningURL(project, CANNOT_PERFORM_GITHUB_REBASE, "GitHub repository ", "'" + repositoryInfo.getName() + "'",
                                         " is not a forked one", repositoryInfo.getHtmlUrl());
      return null;
    }

    final String parentRepoUrl = GithubUrlUtil.getCloneUrl(repositoryInfo.getParent().getFullPath());

    LOG.info("Adding GitHub parent as a remote host");
    indicator.setText("Adding GitHub parent as a remote host...");

    if (GithubUtil.addGithubRemote(project, gitRepository, "upstream", parentRepoUrl)) {
      return parentRepoUrl;
    }
    else {
      return null;
    }
  }

  @Nullable
  private static GithubRepoDetailed loadRepositoryInfo(@NotNull Project project,
                                                       @NotNull GitRepository gitRepository,
                                                       @NotNull ProgressIndicator indicator) {
    final String remoteUrl = GithubUtil.findGithubRemoteUrl(gitRepository);
    if (remoteUrl == null) {
      GithubNotifications.showError(project, CANNOT_PERFORM_GITHUB_REBASE, "Can't find github remote");
      return null;
    }
    final GithubFullPath userAndRepo = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl);
    if (userAndRepo == null) {
      GithubNotifications.showError(project, CANNOT_PERFORM_GITHUB_REBASE, "Can't process remote: " + remoteUrl);
      return null;
    }

    try {
      return GithubUtil.runTask(project, GithubAuthDataHolder.createFromSettings(), indicator,
                                new ThrowableConvertor<GithubAuthData, GithubRepoDetailed, IOException>() {
                                  @NotNull
                                  @Override
                                  public GithubRepoDetailed convert(@NotNull GithubAuthData auth) throws IOException {
                                    return GithubApiUtil.getDetailedRepoInfo(auth, userAndRepo.getUser(), userAndRepo.getRepository());
                                  }
                                });
    }
    catch (GithubOperationCanceledException e) {
      return null;
    }
    catch (IOException e) {
      GithubNotifications.showError(project, "Can't load repository info", e);
      return null;
    }
  }

  private static boolean fetchParent(@NotNull final Project project,
                                     @NotNull final GitRepository repository,
                                     @NotNull final ProgressIndicator indicator) {
    GitFetchResult result = new GitFetcher(project, indicator, false).fetch(repository.getRoot(), "upstream", null);
    if (!result.isSuccess()) {
      GitFetcher.displayFetchResult(project, result, null, result.getErrors());
      return false;
    }
    return true;
  }

  private static void rebaseCurrentBranch(@NotNull final Project project,
                                          @NotNull final GitRepository gitRepository,
                                          @NotNull final ProgressIndicator indicator) {
    final Git git = ServiceManager.getService(project, Git.class);
    final GitPlatformFacade facade = ServiceManager.getService(project, GitPlatformFacade.class);
    GitPreservingProcess process =
      new GitPreservingProcess(project, facade, git, Collections.singletonList(gitRepository), "Rebasing", "upstream/master", indicator,
                               new Runnable() {
                                 @Override
                                 public void run() {
                                   doRebaseCurrentBranch(project, gitRepository.getRoot(), indicator);
                                 }
                               }
      );
    process.execute();
  }

  private static void doRebaseCurrentBranch(@NotNull final Project project,
                                            @NotNull final VirtualFile root,
                                            @NotNull final ProgressIndicator indicator) {
    final GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);

    final GitRebaser rebaser = new GitRebaser(project, ServiceManager.getService(Git.class), indicator);

    final GitLineHandler handler = new GitLineHandler(project, root, GitCommand.REBASE);
    handler.setStdoutSuppressed(false);
    handler.addParameters("upstream/master");

    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    handler.addLineListener(rebaseConflictDetector);

    final GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector =
      new GitUntrackedFilesOverwrittenByOperationDetector(root);
    handler.addLineListener(untrackedFilesDetector);

    GitTask pullTask = new GitTask(project, handler, "Rebasing from upstream/master");
    pullTask.setProgressIndicator(indicator);
    pullTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    pullTask.execute(true, false, new GitTaskResultHandlerAdapter() {
      @Override
      protected void onSuccess() {
        root.refresh(false, true);
        repositoryManager.updateRepository(root);
        GithubNotifications.showInfo(project, "Success", "Successfully rebased GitHub fork");
      }

      @Override
      protected void onFailure() {
        GitUpdateResult result = rebaser.handleRebaseFailure(handler, root, rebaseConflictDetector, untrackedFilesDetector);
        repositoryManager.updateRepository(root);
        if (result == GitUpdateResult.NOTHING_TO_UPDATE ||
            result == GitUpdateResult.SUCCESS ||
            result == GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS) {
          GithubNotifications.showInfo(project, "Success", "Successfully rebased GitHub fork");
        }
      }
    });
  }
}
