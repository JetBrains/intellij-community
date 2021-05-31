// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.DialogManager;
import git4idea.GitUtil;
import git4idea.commands.*;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitRebaseProblemDetector;
import git4idea.rebase.GitRebaser;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitUpdateResult;
import git4idea.util.GitPreservingProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.*;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed;
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.authentication.ui.GithubChooseAccountDialog;
import org.jetbrains.plugins.github.i18n.GithubBundle;
import org.jetbrains.plugins.github.util.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.CHECKOUT;
import static git4idea.fetch.GitFetchSupport.fetchSupport;

public class GithubSyncForkAction extends DumbAwareAction {
  private static final Logger LOG = GithubUtil.LOG;
  private static final String UPSTREAM_REMOTE_NAME = "upstream";
  private static final String ORIGIN_REMOTE_NAME = "origin";

  public GithubSyncForkAction() {
    super(GithubBundle.messagePointer("rebase.action"),
          GithubBundle.messagePointer("rebase.action.description"),
          AllIcons.Vcs.Vendors.Github);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isEnabledAndVisible(e));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();

    Project project = Objects.requireNonNull(e.getData(CommonDataKeys.PROJECT));

    GitRepositoryManager gitRepositoryManager = project.getServiceIfCreated(GitRepositoryManager.class);
    if (gitRepositoryManager == null) {
      LOG.warn("Unable to get the GitRepositoryManager service");
      return;
    }

    if (gitRepositoryManager.getRepositories().size() > 1) {
      GithubNotifications.showError(project,
                                    GithubNotificationIdsHolder.REBASE_MULTI_REPO_NOT_SUPPORTED,
                                    GithubBundle.message("rebase.error"),
                                    GithubBundle.message("rebase.error.multi.repo.not.supported"));
      return;
    }

    GHProjectRepositoriesManager ghRepositoriesManager = project.getServiceIfCreated(GHProjectRepositoriesManager.class);
    if (ghRepositoriesManager == null) {
      LOG.warn("Unable to get the GHProjectRepositoriesManager service");
      return;
    }

    GHGitRepositoryMapping originMapping = ContainerUtil.find(ghRepositoriesManager.getKnownRepositories(), mapping ->
      mapping.getGitRemote().getRemote().getName().equals(ORIGIN_REMOTE_NAME));
    if (originMapping == null) {
      GithubNotifications.showError(project,
                                    GithubNotificationIdsHolder.REBASE_REMOTE_ORIGIN_NOT_FOUND,
                                    GithubBundle.message("rebase.error"),
                                    GithubBundle.message("rebase.error.remote.origin.not.found"));
      return;
    }

    GithubAuthenticationManager authManager = GithubAuthenticationManager.getInstance();
    GithubServerPath serverPath = originMapping.getRepository().getServerPath();
    GithubAccount githubAccount;
    List<GithubAccount> accounts = ContainerUtil.filter(authManager.getAccounts(), account -> serverPath.equals(account.getServer()));
    if (accounts.size() == 0) {
      githubAccount = authManager.requestNewAccountForServer(serverPath, project);
    }
    else if (accounts.size() == 1) {
      githubAccount = accounts.get(0);
    }
    else {
      GithubChooseAccountDialog chooseAccountDialog = new GithubChooseAccountDialog(project,
                                                                                    null,
                                                                                    accounts,
                                                                                    GithubBundle.message("account.choose.for", serverPath),
                                                                                    false,
                                                                                    true);
      DialogManager.show(chooseAccountDialog);
      if (chooseAccountDialog.isOK()) {
        githubAccount = chooseAccountDialog.getAccount();
      }
      else {
        GithubNotifications.showError(project,
                                      GithubNotificationIdsHolder.REBASE_ACCOUNT_NOT_FOUND,
                                      GithubBundle.message("rebase.error"),
                                      GithubBundle.message("rebase.error.no.suitable.account.found"));
        return;
      }
    }
    if (githubAccount == null) {
      GithubNotifications.showError(project,
                                    GithubNotificationIdsHolder.REBASE_ACCOUNT_NOT_FOUND,
                                    GithubBundle.message("rebase.error"),
                                    GithubBundle.message("rebase.error.no.suitable.account.found"));
      return;
    }

    GithubApiRequestExecutor executor = GithubApiRequestExecutorManager.getInstance().getExecutor(githubAccount, project);
    if (executor == null) {
      LOG.warn("Unable to perform the GitHub Sync Fork action. Unable to get GithubApiRequestExecutor");
      return;
    }

    new SyncForkTask(project, executor, Git.getInstance(), githubAccount.getServer(),
                     originMapping.getGitRemote().getRepository(),
                     originMapping.getRepository().getRepositoryPath()).queue();
  }

  private static boolean isEnabledAndVisible(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null || project.isDefault()) return false;

    GHProjectRepositoriesManager repositoriesManager = project.getServiceIfCreated(GHProjectRepositoriesManager.class);
    if (repositoriesManager == null) return false;

    return !repositoriesManager.getKnownRepositories().isEmpty();
  }

  private static class SyncForkTask extends Task.Backgroundable {
    @NotNull private final GithubApiRequestExecutor myRequestExecutor;
    @NotNull private final Git myGit;
    @NotNull private final GithubServerPath myServer;
    @NotNull private final GitRepository myRepository;
    @NotNull private final GHRepositoryPath myRepoPath;

    SyncForkTask(@NotNull Project project,
                 @NotNull GithubApiRequestExecutor requestExecutor,
                 @NotNull Git git,
                 @NotNull GithubServerPath server,
                 @NotNull GitRepository repository,
                 @NotNull GHRepositoryPath repoPath) {
      super(project, GithubBundle.message("rebase.process"));
      myRequestExecutor = requestExecutor;
      myGit = git;
      myServer = server;
      myRepository = repository;
      myRepoPath = repoPath;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myRepository.update();

      GithubRepo parentRepo = validateRepoAndLoadParent(indicator);
      if (parentRepo == null) return;

      GitRemote parentRemote = configureParentRemote(indicator, parentRepo.getFullPath());
      if (parentRemote == null) return;

      String branchName = parentRepo.getDefaultBranch();
      if (branchName == null) {
        GithubNotifications.showError(myProject,
                                      GithubNotificationIdsHolder.REBASE_REPO_NOT_FOUND,
                                      GithubBundle.message("rebase.error"),
                                      GithubBundle.message("rebase.error.no.default.branch"));
        return;
      }

      if (!fetchParent(indicator, parentRemote)) {
        return;
      }

      rebaseCurrentBranch(indicator, parentRemote, branchName);
    }

    @Nullable
    private GithubRepo validateRepoAndLoadParent(@NotNull ProgressIndicator indicator) {
      try {
        GithubRepoDetailed repositoryInfo =
          myRequestExecutor.execute(indicator, GithubApiRequests.Repos.get(myServer, myRepoPath.getOwner(), myRepoPath.getRepository()));
        if (repositoryInfo == null) {
          GithubNotifications.showError(myProject,
                                        GithubNotificationIdsHolder.REBASE_REPO_NOT_FOUND,
                                        GithubBundle.message("rebase.error"),
                                        GithubBundle.message("rebase.error.repo.not.found", myRepoPath.toString()));
          return null;
        }

        GithubRepo parentRepo = repositoryInfo.getParent();
        if (!repositoryInfo.isFork() || parentRepo == null) {
          GithubNotifications.showWarningURL(myProject,
                                             GithubNotificationIdsHolder.REBASE_REPO_IS_NOT_A_FORK,
                                             GithubBundle.message("rebase.error"),
                                             "GitHub repository ", "'" + repositoryInfo.getName() + "'", " is not a fork",
                                             repositoryInfo.getHtmlUrl());
          return null;
        }
        return parentRepo;
      }
      catch (IOException e) {
        GithubNotifications.showError(myProject,
                                      GithubNotificationIdsHolder.REBASE_CANNOT_LOAD_REPO_INFO,
                                      GithubBundle.message("cannot.load.repo.info"),
                                      e);
        return null;
      }
    }

    @Nullable
    private GitRemote configureParentRemote(@NotNull ProgressIndicator indicator, @NotNull GHRepositoryPath parentRepoPath) {
      LOG.info("Configuring upstream remote");
      indicator.setText(GithubBundle.message("rebase.process.configuring.upstream.remote"));

      GitRemote upstreamRemote = findRemote(parentRepoPath);
      if (upstreamRemote != null) {
        LOG.info("Correct upstream remote already exists");
        return upstreamRemote;
      }

      LOG.info("Adding GitHub parent as a remote host");
      indicator.setText(GithubBundle.message("rebase.process.adding.github.parent.as.remote.host"));
      String parentRepoUrl = GithubGitHelper.getInstance().getRemoteUrl(myServer, parentRepoPath);
      try {
        myGit.addRemote(myRepository, UPSTREAM_REMOTE_NAME, parentRepoUrl).throwOnError();
      }
      catch (VcsException e) {
        GithubNotifications.showError(myProject,
                                      GithubNotificationIdsHolder.REBASE_CANNOT_CONFIGURE_UPSTREAM_REMOTE,
                                      GithubBundle.message("rebase.error"),
                                      GithubBundle.message("cannot.configure.remote", UPSTREAM_REMOTE_NAME, e.getMessage()));
        return null;
      }
      myRepository.update();
      upstreamRemote = findRemote(parentRepoPath);
      if (upstreamRemote == null) {
        GithubNotifications.showError(myProject,
                                      GithubNotificationIdsHolder.REBASE_CANNOT_CONFIGURE_UPSTREAM_REMOTE,
                                      GithubBundle.message("rebase.error"),
                                      GithubBundle.message("rebase.error.upstream.not.found", UPSTREAM_REMOTE_NAME));
      }
      return upstreamRemote;
    }

    @Nullable
    private GitRemote findRemote(@NotNull GHRepositoryPath repoPath) {
      return ContainerUtil.find(myRepository.getRemotes(), remote -> {
        String url = remote.getFirstUrl();
        if (url == null || !myServer.matches(url)) return false;

        GHRepositoryPath remotePath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
        return repoPath.equals(remotePath);
      });
    }

    private boolean fetchParent(@NotNull ProgressIndicator indicator, @NotNull GitRemote remote) {
      LOG.info("Fetching upstream");
      indicator.setText(GithubBundle.message("rebase.process.fetching.upstream"));
      return fetchSupport(myProject).fetch(myRepository, remote).showNotificationIfFailed();
    }

    private void rebaseCurrentBranch(@NotNull ProgressIndicator indicator,
                                     @NotNull GitRemote parentRemote,
                                     @NotNull @NlsSafe String branch) {
      String onto = parentRemote.getName() + "/" + branch;
      LOG.info("Rebasing current branch");
      indicator.setText(GithubBundle.message("rebase.process.rebasing.branch.onto", onto));
      try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, GitBundle.message("rebase.git.operation.name"))) {
        List<VirtualFile> rootsToSave = Collections.singletonList(myRepository.getRoot());
        GitSaveChangesPolicy saveMethod = GitVcsSettings.getInstance(myProject).getSaveChangesPolicy();
        GitPreservingProcess process =
          new GitPreservingProcess(myProject, myGit, rootsToSave, GithubBundle.message("rebase.process.operation.title"), onto,
                                   saveMethod, indicator,
                                   () -> doRebaseCurrentBranch(indicator, onto));
        process.execute();
      }
    }

    private void doRebaseCurrentBranch(@NotNull ProgressIndicator indicator, @NotNull String onto) {
      GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(myProject);
      GitRebaser rebaser = new GitRebaser(myProject, myGit, indicator);
      VirtualFile root = myRepository.getRoot();

      GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.REBASE);
      handler.setStdoutSuppressed(false);
      handler.addParameters(onto);

      final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
      handler.addLineListener(rebaseConflictDetector);

      final GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector =
        new GitUntrackedFilesOverwrittenByOperationDetector(root);
      final GitLocalChangesWouldBeOverwrittenDetector localChangesDetector = new GitLocalChangesWouldBeOverwrittenDetector(root, CHECKOUT);
      handler.addLineListener(untrackedFilesDetector);
      handler.addLineListener(localChangesDetector);
      handler.addLineListener(GitStandardProgressAnalyzer.createListener(indicator));

      String oldText = indicator.getText();
      indicator.setText(GithubBundle.message("rebase.process.rebasing.onto", onto));
      GitCommandResult rebaseResult = myGit.runCommand(handler);
      indicator.setText(oldText);
      repositoryManager.updateRepository(root);
      if (rebaseResult.success()) {
        root.refresh(false, true);
        GithubNotifications.showInfo(myProject,
                                     GithubNotificationIdsHolder.REBASE_SUCCESS,
                                     GithubBundle.message("rebase.process.success"),
                                     "");
      }
      else {
        GitUpdateResult result = rebaser.handleRebaseFailure(handler, root, rebaseResult, rebaseConflictDetector,
                                                             untrackedFilesDetector, localChangesDetector);
        if (result == GitUpdateResult.NOTHING_TO_UPDATE ||
            result == GitUpdateResult.SUCCESS ||
            result == GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS) {
          GithubNotifications.showInfo(myProject,
                                       GithubNotificationIdsHolder.REBASE_SUCCESS,
                                       GithubBundle.message("rebase.process.success"),
                                       "");
        }
      }
    }
  }
}