// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.containers.MultiMap;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.i18n.GitBundle;
import git4idea.push.GitPushParamsImpl;
import git4idea.repo.GitRefUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static git4idea.GitNotificationIdsHolder.REMOTE_BRANCH_DELETION_ERROR;
import static git4idea.GitNotificationIdsHolder.REMOTE_BRANCH_DELETION_SUCCESS;
import static git4idea.branch.GitBranchUiHandler.DeleteRemoteBranchDecision;
import static git4idea.branch.GitBranchUiHandler.DeleteRemoteBranchDecision.CANCEL;
import static git4idea.branch.GitBranchUiHandler.DeleteRemoteBranchDecision.DELETE_WITH_TRACKING;

class GitDeleteRemoteBranchOperation extends GitBranchOperation {
  private final List<String> myBranchNames;

  GitDeleteRemoteBranchOperation(@NotNull Project project, @NotNull Git git,
                                 @NotNull GitBranchUiHandler handler, @NotNull List<? extends GitRepository> repositories,
                                 @NotNull List<String> names) {
    super(project, git, handler, repositories);
    myBranchNames = names;
  }

  @Override
  protected void execute() {
    Collection<GitRepository> repositories = getRepositories();
    Collection<String> allTrackingBranches = new ArrayList<>();
    MultiMap<String, String> branchToCommonTrackingBranches = new MultiMap<>();
    Ref<DeleteRemoteBranchDecision> decisionRef = Ref.create();

    for (String branchName : myBranchNames) {
      Collection<String> commonTrackingBranches = new ArrayList<>(getCommonTrackingBranches(branchName, repositories));
      // don't propose to remove current branch even if it tracks the remote branch
      for (GitRepository repository : repositories) {
        String currentBranch = repository.getCurrentBranchName();
        if (currentBranch != null) {
          commonTrackingBranches.remove(currentBranch);
        }
      }
      allTrackingBranches.addAll(commonTrackingBranches);
      branchToCommonTrackingBranches.put(branchName, commonTrackingBranches);
    }

    ApplicationManager.getApplication().invokeAndWait(() -> decisionRef.set(
      myUiHandler.confirmRemoteBranchDeletion(myBranchNames, allTrackingBranches, repositories)));
    DeleteRemoteBranchDecision decision = decisionRef.get();
    if (decision == CANCEL) return;

    for (String branchName : myBranchNames) {
      boolean deletedSuccessfully = doDeleteRemote(branchName, repositories);
      if (deletedSuccessfully) {
        Collection<String> successfullyDeletedLocalBranches = new ArrayList<>(1);
        if (decision == DELETE_WITH_TRACKING) {
          for (String branch : branchToCommonTrackingBranches.get(branchName)) {
            getIndicator().setText(GitBundle.message("delete.remote.branch.operation.deleting.process", branch));
            new GitDeleteBranchOperation(myProject, myGit, myUiHandler, repositories, branch) {
              @Override
              protected void notifySuccess() {
                // do nothing - will display a combo notification for all deleted branches below
                successfullyDeletedLocalBranches.add(branch);
              }
            }.execute();
          }
        }
        notifySuccessfulDeletion(branchName, successfullyDeletedLocalBranches);
      }
    }
  }

  @Override
  protected void rollback() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected @NotNull String getSuccessMessage() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected @NotNull String getRollbackProposal() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected @NotNull String getOperationName() {
    throw new UnsupportedOperationException();
  }

  private static @Unmodifiable @NotNull Collection<String> getCommonTrackingBranches(@NotNull String remoteBranch,
                                                                                     @NotNull Collection<? extends GitRepository> repositories) {
    return new GitMultiRootBranchConfig(repositories).getCommonTrackingBranches(remoteBranch);
  }

  private boolean doDeleteRemote(@NotNull String branchName, @NotNull Collection<? extends GitRepository> repositories) {
    Couple<String> pair = splitNameOfRemoteBranch(branchName);
    String remoteName = pair.getFirst();
    String branch = pair.getSecond();

    GitCompoundResult result = new GitCompoundResult(myProject);
    for (GitRepository repository : repositories) {
      GitCommandResult res;
      GitRemote remote = getRemoteByName(repository, remoteName);
      if (remote == null) {
        LOG.error("Couldn't find remote by name: " + remoteName);
        res = GitCommandResult.error(GitBundle.message("delete.remote.branch.operation.couldn.t.find.remote.by.name", remoteName));
      }
      else {
        String fullBranchName = GitRefUtil.addRefsHeadsPrefixIfNeeded(branch);
        res = pushDeletion(repository, remote, fullBranchName);
        if (!res.success() && isAlreadyDeletedError(res.getErrorOutputAsJoinedString())) {
          res = myGit.remotePrune(repository, remote);
        }
      }
      result.append(repository, res);
      repository.update();
    }
    if (!result.totalSuccess()) {
      VcsNotifier.getInstance(myProject).notifyError(
        REMOTE_BRANCH_DELETION_ERROR, GitBundle.message("delete.remote.branch.operation.failed.to.delete.remote.branch", branchName),
        result.getErrorOutputWithReposIndication(),
        true);
    }
    return result.totalSuccess();
  }

  private static boolean isAlreadyDeletedError(@NotNull String errorOutput) {
    return errorOutput.contains("remote ref does not exist"); //NON-NLS
  }

  /**
   * Returns the remote and the "local" name of a remote branch.
   * Expects branch in format "origin/master", i.e. remote/branch
   */
  private static Couple<String> splitNameOfRemoteBranch(String branchName) {
    int firstSlash = branchName.indexOf('/');
    String remoteName = firstSlash > -1 ? branchName.substring(0, firstSlash) : branchName;
    String remoteBranchName = branchName.substring(firstSlash + 1);
    return Couple.of(remoteName, remoteBranchName);
  }

  private @NotNull GitCommandResult pushDeletion(@NotNull GitRepository repository, @NotNull GitRemote remote, @NotNull String branchName) {
    return myGit.push(repository, new GitPushParamsImpl(remote, ":" + branchName, false, false, false, null, Collections.emptyList()));
  }

  private static @Nullable GitRemote getRemoteByName(@NotNull GitRepository repository, @NotNull String remoteName) {
    for (GitRemote remote : repository.getRemotes()) {
      if (remote.getName().equals(remoteName)) {
        return remote;
      }
    }
    return null;
  }

  private void notifySuccessfulDeletion(@NotNull String remoteBranchName, @NotNull Collection<String> localBranches) {
    String message = "";
    if (!localBranches.isEmpty()) {
      message = GitBundle.message("delete.remote.branch.operation.also.deleted.local.branches",
                                  localBranches.size(),
                                  StringUtil.join(localBranches, ", "));
    }
    VcsNotifier.getInstance(myProject).notifySuccess(
      REMOTE_BRANCH_DELETION_SUCCESS,
      GitBundle.message("delete.remote.branch.operation.deleted.remote.branch", remoteBranchName),
      message);
  }
}
