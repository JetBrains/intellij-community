// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.actions.BasicAction;
import git4idea.commands.*;
import git4idea.config.GitVcsSettings;
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
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GithubGitHelper;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.CHECKOUT;
import static git4idea.fetch.GitFetchSupport.fetchSupport;

public class GithubRebaseAction extends AbstractGithubUrlGroupingAction {
  private static final Logger LOG = GithubUtil.LOG;
  private static final String CANNOT_PERFORM_GITHUB_REBASE = "Can't perform GitHub rebase";

  public GithubRebaseAction() {
    super("Rebase my GitHub fork", "Rebase your GitHub forked repository relative to the origin", AllIcons.Vcs.Vendors.Github);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e,
                              @NotNull Project project,
                              @NotNull GitRepository repository,
                              @NotNull GitRemote remote,
                              @NotNull String remoteUrl,
                              @NotNull GithubAccount account) {
    BasicAction.saveAll();
    GithubApiRequestExecutor executor = GithubApiRequestExecutorManager.getInstance().getExecutor(account, project);
    if (executor == null) return;

    GithubFullPath repoPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl);
    if (repoPath == null) {
      GithubNotifications.showError(project, CANNOT_PERFORM_GITHUB_REBASE, "Invalid Github remote: " + remoteUrl);
      return;
    }
    new RebaseTask(project, executor, Git.getInstance(), account.getServer(), repository, repoPath, "upstream/master").queue();
  }

  private static class RebaseTask extends Task.Backgroundable {
    @NotNull private final GithubApiRequestExecutor myRequestExecutor;
    @NotNull private final Git myGit;
    @NotNull private final GithubServerPath myServer;
    @NotNull private final GitRepository myRepository;
    @NotNull private final GithubFullPath myRepoPath;
    @NotNull private final String myOnto;

    RebaseTask(@NotNull Project project,
                      @NotNull GithubApiRequestExecutor requestExecutor,
                      @NotNull Git git,
                      @NotNull GithubServerPath server,
                      @NotNull GitRepository repository,
                      @NotNull GithubFullPath repoPath,
                      @NotNull String rebaseOnto) {
      super(project, "Rebasing GitHub Fork...");
      myRequestExecutor = requestExecutor;
      myGit = git;
      myServer = server;
      myRepository = repository;
      myRepoPath = repoPath;
      myOnto = rebaseOnto;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      myRepository.update();
      String upstreamRemoteUrl = findUpstreamRemoteUrl();
      if (upstreamRemoteUrl == null) {
        indicator.setText("Configuring upstream remote...");
        LOG.info("Configuring upstream remote");
        if ((upstreamRemoteUrl = configureUpstreamRemote(indicator)) == null) return;
      }

      if (isUpstreamWithSameUsername(indicator, upstreamRemoteUrl)) {
        GithubNotifications.showError(myProject, CANNOT_PERFORM_GITHUB_REBASE,
                                      "Configured upstream seems to be your own repository: " + upstreamRemoteUrl);
        return;
      }

      LOG.info("Fetching upstream");
      indicator.setText("Fetching upstream...");
      if (!fetchParent(indicator)) {
        return;
      }

      LOG.info("Rebasing current branch");
      indicator.setText("Rebasing current branch...");
      rebaseCurrentBranch(indicator);
    }

    @Nullable
    private String findUpstreamRemoteUrl() {
      return myRepository.getRemotes().stream()
        .filter(remote -> remote.getName().equals("upstream") &&
                          remote.getFirstUrl() != null &&
                          myServer.matches(remote.getFirstUrl()))
        .findFirst()
        .map(GitRemote::getFirstUrl).orElse(null);
    }

    private boolean isUpstreamWithSameUsername(@NotNull ProgressIndicator indicator, @NotNull String upstreamRemoteUrl) {
      try {
        GithubFullPath userAndRepo = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(upstreamRemoteUrl);
        if (userAndRepo == null) {
          GithubNotifications.showError(myProject, CANNOT_PERFORM_GITHUB_REBASE, "Can't validate upstream remote: " + upstreamRemoteUrl);
          return true;
        }
        String username = myRequestExecutor.execute(indicator, GithubApiRequests.CurrentUser.get(myServer)).getLogin();
        return userAndRepo.getUser().equals(username);
      }
      catch (IOException e) {
        GithubNotifications.showError(myProject, CANNOT_PERFORM_GITHUB_REBASE, "Can't get user information");
        return true;
      }
    }

    @Nullable
    private String configureUpstreamRemote(@NotNull ProgressIndicator indicator) {
      GithubRepoDetailed repositoryInfo = loadRepositoryInfo(indicator, myRepoPath);
      if (repositoryInfo == null) {
        return null;
      }

      if (!repositoryInfo.isFork() || repositoryInfo.getParent() == null) {
        GithubNotifications
          .showWarningURL(myProject, CANNOT_PERFORM_GITHUB_REBASE, "GitHub repository ", "'" + repositoryInfo.getName() + "'",
                          " is not a fork", repositoryInfo.getHtmlUrl());
        return null;
      }

      String parentRepoUrl = GithubGitHelper.getInstance().getRemoteUrl(myServer, repositoryInfo.getParent().getFullPath());

      LOG.info("Adding GitHub parent as a remote host");
      indicator.setText("Adding GitHub parent as a remote host...");
      try {
        myGit.addRemote(myRepository, "upstream", parentRepoUrl).throwOnError();
      }
      catch (VcsException e) {
        GithubNotifications
          .showError(myProject, CANNOT_PERFORM_GITHUB_REBASE, "Could not configure \"upstream\" remote:\n" + e.getMessage());
        return null;
      }
      myRepository.update();
      return parentRepoUrl;
    }

    @Nullable
    private GithubRepoDetailed loadRepositoryInfo(@NotNull ProgressIndicator indicator, @NotNull GithubFullPath fullPath) {
      try {
        GithubRepoDetailed repo =
          myRequestExecutor.execute(indicator, GithubApiRequests.Repos.get(myServer, fullPath.getUser(), fullPath.getRepository()));
        if (repo == null) GithubNotifications.showError(myProject, "Repository " + fullPath.toString() + " was not found", "");
        return repo;
      }
      catch (IOException e) {
        GithubNotifications.showError(myProject, "Can't load repository info", e);
        return null;
      }
    }

    private boolean fetchParent(@NotNull final ProgressIndicator indicator) {
      GitRemote remote = GitUtil.findRemoteByName(myRepository, "upstream");
      if (remote == null) {
        LOG.warn("Couldn't find remote " + " remoteName " + " in " + myRepository);
        return false;
      }
      return fetchSupport(myProject).fetch(myRepository, remote).showNotificationIfFailed();
    }

    private void rebaseCurrentBranch(@NotNull ProgressIndicator indicator) {
      try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, "Rebase")) {
        List<VirtualFile> rootsToSave = Collections.singletonList(myRepository.getRoot());
        GitPreservingProcess process = new GitPreservingProcess(myProject, myGit, rootsToSave, "Rebasing", myOnto,
                                                                GitVcsSettings.UpdateChangesPolicy.STASH, indicator,
                                                                () -> doRebaseCurrentBranch(indicator));
        process.execute();
      }
    }

    private void doRebaseCurrentBranch(@NotNull ProgressIndicator indicator) {
      GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(myProject);
      GitRebaser rebaser = new GitRebaser(myProject, myGit, indicator);
      VirtualFile root = myRepository.getRoot();

      GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.REBASE);
      handler.setStdoutSuppressed(false);
      handler.addParameters(myOnto);

      final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
      handler.addLineListener(rebaseConflictDetector);

      final GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector =
        new GitUntrackedFilesOverwrittenByOperationDetector(root);
      final GitLocalChangesWouldBeOverwrittenDetector localChangesDetector = new GitLocalChangesWouldBeOverwrittenDetector(root, CHECKOUT);
      handler.addLineListener(untrackedFilesDetector);
      handler.addLineListener(localChangesDetector);
      handler.addLineListener(GitStandardProgressAnalyzer.createListener(indicator));

      String oldText = indicator.getText();
      indicator.setText("Rebasing onto " + myOnto + "...");
      GitCommandResult rebaseResult = myGit.runCommand(handler);
      indicator.setText(oldText);
      repositoryManager.updateRepository(root);
      if (rebaseResult.success()) {
        root.refresh(false, true);
        GithubNotifications.showInfo(myProject, "Success", "Successfully rebased GitHub fork");
      }
      else {
        GitUpdateResult result = rebaser.handleRebaseFailure(handler, root, rebaseResult, rebaseConflictDetector,
                                                             untrackedFilesDetector, localChangesDetector);
        if (result == GitUpdateResult.NOTHING_TO_UPDATE ||
            result == GitUpdateResult.SUCCESS ||
            result == GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS) {
          GithubNotifications.showInfo(myProject, "Success", "Successfully rebased GitHub fork");
        }
      }
    }
  }
}
