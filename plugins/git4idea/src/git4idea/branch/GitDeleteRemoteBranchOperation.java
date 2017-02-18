/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.ui.UIUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class GitDeleteRemoteBranchOperation extends GitBranchOperation {
  private final String myBranchName;

  public GitDeleteRemoteBranchOperation(@NotNull Project project, @NotNull Git git,
                                        @NotNull GitBranchUiHandler handler, @NotNull List<GitRepository> repositories,
                                        @NotNull String name) {
    super(project, git, handler, repositories);
    myBranchName = name;
  }

  @Override
  protected void execute() {
    final Collection<GitRepository> repositories = getRepositories();
    final Collection<String> trackingBranches = findTrackingBranches(myBranchName, repositories);
    String currentBranch = GitBranchUtil.getCurrentBranchOrRev(repositories);
    boolean currentBranchTracksBranchToDelete = false;
    if (trackingBranches.contains(currentBranch)) {
      currentBranchTracksBranchToDelete = true;
      trackingBranches.remove(currentBranch);
    }

    final AtomicReference<DeleteRemoteBranchDecision> decision = new AtomicReference<>();
    final boolean finalCurrentBranchTracksBranchToDelete = currentBranchTracksBranchToDelete;
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        decision.set(confirmBranchDeletion(myBranchName, trackingBranches, finalCurrentBranchTracksBranchToDelete, repositories));
      }
    });


    if (decision.get().delete()) {
      boolean deletedSuccessfully = doDeleteRemote(myBranchName, repositories);
      if (deletedSuccessfully) {
        final Collection<String> successfullyDeletedLocalBranches = new ArrayList<>(1);
        if (decision.get().deleteTracking()) {
          for (final String branch : trackingBranches) {
            getIndicator().setText("Deleting " + branch);
            new GitDeleteBranchOperation(myProject, myGit, myUiHandler, repositories, branch) {
              @Override
              protected void notifySuccess(@NotNull String message) {
                // do nothing - will display a combo notification for all deleted branches below
                successfullyDeletedLocalBranches.add(branch);
              }
            }.execute();
          }
        }
        notifySuccessfulDeletion(myBranchName, successfullyDeletedLocalBranches);
      }
    }

  }

  @Override
  protected void rollback() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public String getSuccessMessage() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  protected String getOperationName() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  private static Collection<String> findTrackingBranches(@NotNull String remoteBranch, @NotNull Collection<GitRepository> repositories) {
    return new GitMultiRootBranchConfig(repositories).getTrackingBranches(remoteBranch);
  }

  private boolean doDeleteRemote(@NotNull String branchName, @NotNull Collection<GitRepository> repositories) {
    Couple<String> pair = splitNameOfRemoteBranch(branchName);
    String remoteName = pair.getFirst();
    String branch = pair.getSecond();

    GitCompoundResult result = new GitCompoundResult(myProject);
    for (GitRepository repository : repositories) {
      GitCommandResult res;
      GitRemote remote = getRemoteByName(repository, remoteName);
      if (remote == null) {
        String error = "Couldn't find remote by name: " + remoteName;
        LOG.error(error);
        res = GitCommandResult.error(error);
      }
      else {
        res = pushDeletion(repository, remote, branch);
        if (!res.success() && isAlreadyDeletedError(res.getErrorOutputAsJoinedString())) {
          res = myGit.remotePrune(repository, remote);
        }
      }
      result.append(repository, res);
      repository.update();
    }
    if (!result.totalSuccess()) {
      VcsNotifier.getInstance(myProject).notifyError("Failed to delete remote branch " + branchName,
                                                       result.getErrorOutputWithReposIndication());
    }
    return result.totalSuccess();
  }

  private static boolean isAlreadyDeletedError(@NotNull String errorOutput) {
    return errorOutput.contains("remote ref does not exist");
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

  @NotNull
  private GitCommandResult pushDeletion(@NotNull GitRepository repository, @NotNull GitRemote remote, @NotNull String branchName) {
    return myGit.push(repository, remote, ":" + branchName, false, false, null);
  }

  @Nullable
  private static GitRemote getRemoteByName(@NotNull GitRepository repository, @NotNull String remoteName) {
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
      message = "Also deleted local " + StringUtil.pluralize("branch", localBranches.size()) + ": " + StringUtil.join(localBranches, ", ");
    }
    VcsNotifier.getInstance(myProject).notifySuccess("Deleted remote branch " + remoteBranchName,
                                                     message);
  }

  private DeleteRemoteBranchDecision confirmBranchDeletion(@NotNull String branchName, @NotNull Collection<String> trackingBranches,
                                                           boolean currentBranchTracksBranchToDelete,
                                                           @NotNull Collection<GitRepository> repositories) {
    String title = "Delete Remote Branch";
    String message = "Delete remote branch " + branchName;

    boolean delete;
    final boolean deleteTracking;
    if (trackingBranches.isEmpty()) {
      delete = Messages.showYesNoDialog(myProject, message, title, "Delete", "Cancel", Messages.getQuestionIcon()) == Messages.YES;
      deleteTracking = false;
    }
    else {
      if (currentBranchTracksBranchToDelete) {
        message += "\n\nCurrent branch " + GitBranchUtil.getCurrentBranchOrRev(repositories) + " tracks " + branchName + " but won't be deleted.";
      }
      final String checkboxMessage;
      if (trackingBranches.size() == 1) {
        checkboxMessage = "Delete tracking local branch " + trackingBranches.iterator().next() + " as well";
      }
      else {
        checkboxMessage = "Delete tracking local branches " + StringUtil.join(trackingBranches, ", ");
      }

      final AtomicBoolean deleteChoice = new AtomicBoolean();
      delete = MessageDialogBuilder.yesNo(title, message).project(myProject).yesText("Delete").noText("Cancel").doNotAsk(
        new DialogWrapper.DoNotAskOption.Adapter() {
          @Override
          public void rememberChoice(boolean isSelected, int exitCode) {
            deleteChoice.set(isSelected);
          }

          @NotNull
          @Override
          public String getDoNotShowMessage() {
            return checkboxMessage;
          }
        }).show() == Messages.YES;
      deleteTracking = deleteChoice.get();
    }
    return new DeleteRemoteBranchDecision(delete, deleteTracking);
  }

  private static class DeleteRemoteBranchDecision {
    private final boolean delete;
    private final boolean deleteTracking;

    private DeleteRemoteBranchDecision(boolean delete, boolean deleteTracking) {
      this.delete = delete;
      this.deleteTracking = deleteTracking;
    }

    public boolean delete() {
      return delete;
    }

    public boolean deleteTracking() {
      return deleteTracking;
    }
  }

}