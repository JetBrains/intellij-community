/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.Consumer;
import git4idea.*;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.jgit.GitHttpAdapter;
import git4idea.push.GitSimplePushResult;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchUiUtil;
import git4idea.ui.branch.GitCompareBranchesDialog;
import git4idea.ui.branch.GitMultiRootBranchConfig;
import git4idea.util.GitCommitCompareInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Kirill Likhodedov
 */
final class GitBrancherImpl implements GitBrancher {

  private static final Logger LOG = Logger.getInstance(GitBrancherImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final PlatformFacade myFacade;
  @NotNull private final Git myGit;

  GitBrancherImpl(@NotNull Project project, @NotNull PlatformFacade platformFacade, @NotNull Git git) {
    myProject = project;
    myFacade = platformFacade;
    myGit = git;
  }
  
  @NotNull
  private static String getCurrentBranchOrRev(@NotNull List<GitRepository> repositories) {
    if (repositories.size() > 1) {
      GitMultiRootBranchConfig multiRootBranchConfig = new GitMultiRootBranchConfig(repositories);
      String currentBranch = multiRootBranchConfig.getCurrentBranch();
      LOG.assertTrue(currentBranch != null, "Repositories have unexpectedly diverged. " + multiRootBranchConfig);
      return currentBranch;
    }
    else {
      assert !repositories.isEmpty() : "No repositories passed to GitBranchOperationsProcessor.";
      GitRepository repository = repositories.iterator().next();
      return GitBranchUiUtil.getBranchNameOrRev(repository);
    }
  }

  @Override
  public void checkoutNewBranch(@NotNull final String name, @NotNull final List<GitRepository> repositories,
                                @Nullable final Consumer<Boolean> resultHandler) {
    new CommonBackgroundTask(myProject, "Checking out new branch " + name, null) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        doCheckoutNewBranch(name, repositories, resultHandler, indicator);
      }
    }.runInBackground();
  }

  @Override
  public void createNewTag(@NotNull final String name, @NotNull final String reference, @NotNull final List<GitRepository> repositories,
                           @Nullable Runnable callInAwtLater) {
    new CommonBackgroundTask(myProject, "Checking out new branch " + name, callInAwtLater) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        for (GitRepository repository : repositories) {
          myGit.createNewTag(repository, name, null, reference);
        }
      }
    }.runInBackground();
  }

  private void doCheckoutNewBranch(@NotNull final String name, @NotNull List<GitRepository> repositories,
                                   @Nullable Consumer<Boolean> resultHandler, @NotNull ProgressIndicator indicator) {
    new GitCheckoutNewBranchOperation(myProject, myGit, repositories, name, getCurrentBranchOrRev(repositories), resultHandler, indicator)
      .execute();
  }

  @Override
  public void checkoutNewBranchStartingFrom(@NotNull String newBranchName, @NotNull String startPoint,
                                            @NotNull List<GitRepository> repositories, @Nullable Runnable callInAwtLater) {
    commonCheckout(startPoint, newBranchName, repositories, callInAwtLater);
  }

  @Override
  public void checkout(@NotNull final String reference, @NotNull List<GitRepository> repositories, @Nullable Runnable callInAwtLater) {
    commonCheckout(reference, null, repositories, callInAwtLater);
  }

  private void commonCheckout(@NotNull final String reference, @Nullable final String newBranch, @NotNull final List<GitRepository> repositories,
                              @Nullable Runnable callInAwtLater) {
    new CommonBackgroundTask(myProject, "Checking out " + reference, callInAwtLater) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        doCheckout(indicator, reference, repositories, newBranch);
      }
    }.runInBackground();
  }

  private void doCheckout(@NotNull ProgressIndicator indicator, @NotNull String reference, @NotNull List<GitRepository> repositories,
                          @Nullable String newBranch) {
    new GitCheckoutOperation(myProject, myGit, repositories, reference, newBranch, getCurrentBranchOrRev(repositories), indicator)
      .execute();
  }

  @Override
  public void deleteBranch(@NotNull final String branchName, @NotNull final List<GitRepository> repositories) {
    new CommonBackgroundTask(myProject, "Deleting " + branchName, null) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        doDelete(branchName, repositories, indicator);
      }
    }.runInBackground();
  }

  private void doDelete(@NotNull String branchName, @NotNull List<GitRepository> repositories, @NotNull ProgressIndicator indicator) {
    new GitDeleteBranchOperation(myProject, myGit, repositories, branchName, getCurrentBranchOrRev(repositories), indicator).execute();
  }

  @Override
  public void deleteRemoteBranch(@NotNull final String branchName, @NotNull final List<GitRepository> repositories) {
    final Collection<String> trackingBranches = findTrackingBranches(branchName, repositories);
    String currentBranch = getCurrentBranchOrRev(repositories);
    boolean currentBranchTracksBranchToDelete = false;
    if (trackingBranches.contains(currentBranch)) {
      currentBranchTracksBranchToDelete = true;
      trackingBranches.remove(currentBranch);
    }

    final DeleteRemoteBranchDecision decision = confirmBranchDeletion(branchName, trackingBranches, currentBranchTracksBranchToDelete,
                                                                      repositories);

    if (decision.delete()) {
      new CommonBackgroundTask(myProject, "Deleting " + branchName, null) {
        @Override public void execute(@NotNull ProgressIndicator indicator) {
          boolean deletedSuccessfully = doDeleteRemote(branchName, repositories);
          if (deletedSuccessfully) {
            final Collection<String> successfullyDeletedLocalBranches = new ArrayList<String>(1);
            if (decision.deleteTracking()) {
              for (final String branch : trackingBranches) {
                indicator.setText("Deleting " + branch);
                new GitDeleteBranchOperation(myProject, myGit, repositories, branch, getCurrentBranchOrRev(repositories), indicator) {
                  @Override
                  protected void notifySuccess(@NotNull String message) {
                    // do nothing - will display a combo notification for all deleted branches below
                    successfullyDeletedLocalBranches.add(branch);
                  }
                }.execute();
              }
            }
            notifySuccessfulDeletion(branchName, successfullyDeletedLocalBranches);
          }
        }
      }.runInBackground();
    }
  }

  @NotNull
  private static Collection<String> findTrackingBranches(@NotNull String remoteBranch, @NotNull List<GitRepository> repositories) {
    return new GitMultiRootBranchConfig(repositories).getTrackingBranches(remoteBranch);
  }

  private boolean doDeleteRemote(@NotNull String branchName, @NotNull List<GitRepository> repositories) {
    GitCompoundResult result = new GitCompoundResult(myProject);
    for (GitRepository repository : repositories) {
      Pair<String, String> pair = GitBranch.splitNameOfRemoteBranch(branchName);
      String remote = pair.getFirst();
      String branch = pair.getSecond();
      GitCommandResult res = pushDeletion(repository, remote, branch);
      result.append(repository, res);
      repository.update(GitRepository.TrackedTopic.BRANCHES);
    }
    if (!result.totalSuccess()) {
      Notificator.getInstance(myProject).notifyError("Failed to delete remote branch " + branchName,
                                                             result.getErrorOutputWithReposIndication());
    }
    return result.totalSuccess();
  }

  @NotNull
  private GitCommandResult pushDeletion(@NotNull GitRepository repository, @NotNull String remoteName, @NotNull String branchName) {
    GitRemote remote = getRemoteByName(repository, remoteName);
    if (remote == null) {
      return pushDeletionNatively(repository, remoteName, branchName);
    }

    String remoteUrl = remote.getFirstUrl();
    if (remoteUrl != null && GitHttpAdapter.shouldUseJGit(remoteUrl)) {
      String fullBranchName = branchName.startsWith(GitBranch.REFS_HEADS_PREFIX) ? branchName : GitBranch.REFS_HEADS_PREFIX + branchName;
      String spec = ":" + fullBranchName;
      GitSimplePushResult simplePushResult = GitHttpAdapter.push(repository, remote.getName(), remoteUrl, spec);
      return convertSimplePushResultToCommandResult(simplePushResult);
    }
    else {
      return pushDeletionNatively(repository, remoteName, branchName);
    }
  }

  @NotNull
  private GitCommandResult pushDeletionNatively(@NotNull GitRepository repository, @NotNull String remoteName, @NotNull String branchName) {
    return myGit.push(repository, remoteName, ":" + branchName);
  }

  @NotNull
  private static GitCommandResult convertSimplePushResultToCommandResult(@NotNull GitSimplePushResult result) {
    boolean success = result.getType() == GitSimplePushResult.Type.SUCCESS;
    return new GitCommandResult(success, -1, success ? Collections.<String>emptyList() : Collections.singletonList(result.getOutput()),
                                success ? Collections.singletonList(result.getOutput()) : Collections.<String>emptyList());
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
    Notificator.getInstance(myProject).notify(GitVcs.NOTIFICATION_GROUP_ID, "Deleted remote branch " + remoteBranchName,
                                                      message, NotificationType.INFORMATION);
  }

  private DeleteRemoteBranchDecision confirmBranchDeletion(@NotNull String branchName, @NotNull Collection<String> trackingBranches,
                                                           boolean currentBranchTracksBranchToDelete,
                                                           @NotNull List<GitRepository> repositories) {
    String title = "Delete Remote Branch";
    String message = "Delete remote branch " + branchName;

    boolean delete;
    final boolean deleteTracking;
    if (trackingBranches.isEmpty()) {
      delete = Messages.showYesNoDialog(myProject, message, title, "Delete", "Cancel", Messages.getQuestionIcon()) == Messages.OK;
      deleteTracking = false;
    }
    else {
      if (currentBranchTracksBranchToDelete) {
        message += "\n\nCurrent branch " + getCurrentBranchOrRev(repositories) + " tracks " + branchName + " but won't be deleted.";
      }
      final String checkboxMessage;
      if (trackingBranches.size() == 1) {
        checkboxMessage = "Delete tracking local branch " + trackingBranches.iterator().next() + " as well";
      }
      else {
        checkboxMessage = "Delete tracking local branches " + StringUtil.join(trackingBranches, ", ");
      }

      final AtomicBoolean deleteChoice = new AtomicBoolean();
      delete = Messages.OK == Messages.showYesNoDialog(message, title, "Delete", "Cancel", Messages.getQuestionIcon(), new DialogWrapper.DoNotAskOption() {
        @Override
        public boolean isToBeShown() {
          return true;
        }

        @Override
        public void setToBeShown(boolean value, int exitCode) {
          deleteChoice.set(!value);
        }

        @Override
        public boolean canBeHidden() {
          return true;
        }

        @Override
        public boolean shouldSaveOptionsOnCancel() {
          return false;
        }

        @Override
        public String getDoNotShowMessage() {
          return checkboxMessage;
        }
      });
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

  @Override
  public void compare(@NotNull final String branchName, @NotNull final List<GitRepository> repositories,
                      @NotNull final GitRepository selectedRepository) {
    new CommonBackgroundTask(myProject, "Comparing with " + branchName, null) {
  
      private GitCommitCompareInfo myCompareInfo;
  
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        myCompareInfo = loadCommitsToCompare(repositories, branchName);
      }
  
      @Override
      public void onSuccess() {
        if (myCompareInfo == null) {
          LOG.error("The task to get compare info didn't finish. Repositories: \n" + repositories + "\nbranch name: " + branchName);
          return;
        }
        displayCompareDialog(branchName, getCurrentBranchOrRev(repositories), myCompareInfo, selectedRepository);
      }
    }.runInBackground();
  }

  private GitCommitCompareInfo loadCommitsToCompare(List<GitRepository> repositories, String branchName) {
    GitCommitCompareInfo compareInfo = new GitCommitCompareInfo();
    for (GitRepository repository : repositories) {
      compareInfo.put(repository, loadCommitsToCompare(repository, branchName));
      compareInfo.put(repository, loadTotalDiff(repository, branchName));
    }
    return compareInfo;
  }

  @NotNull
  private static Collection<Change> loadTotalDiff(@NotNull GitRepository repository, @NotNull String branchName) {
    try {
      return GitChangeUtils.getDiff(repository.getProject(), repository.getRoot(), "HEAD", branchName, null);
    }
    catch (VcsException e) {
      // we treat it as critical and report an error
      throw new GitExecutionException("Couldn't get [git diff " + branchName + "] on repository [" + repository.getRoot() + "]", e);
    }
  }

  @NotNull
  private Pair<List<GitCommit>, List<GitCommit>> loadCommitsToCompare(@NotNull GitRepository repository, @NotNull final String branchName) {
    final List<GitCommit> headToBranch;
    final List<GitCommit> branchToHead;
    try {
      headToBranch = GitHistoryUtils.history(myProject, repository.getRoot(), ".." + branchName);
      branchToHead = GitHistoryUtils.history(myProject, repository.getRoot(), branchName + "..");
    }
    catch (VcsException e) {
      // we treat it as critical and report an error
      throw new GitExecutionException("Couldn't get [git log .." + branchName + "] on repository [" + repository.getRoot() + "]", e);
    }
    return Pair.create(headToBranch, branchToHead);
  }
  
  private void displayCompareDialog(@NotNull String branchName, @NotNull String currentBranch, @NotNull GitCommitCompareInfo compareInfo,
                                    @NotNull GitRepository selectedRepository) {
    if (compareInfo.isEmpty()) {
      Messages.showInfoMessage(myProject, String.format("<html>There are no changes between <code>%s</code> and <code>%s</code></html>",
                                                        currentBranch, branchName), "No Changes Detected");
    }
    else {
      new GitCompareBranchesDialog(myProject, branchName, currentBranch, compareInfo, selectedRepository).show();
    }
  }

  @Override
  public void merge(@NotNull final String branchName, @NotNull final DeleteOnMergeOption deleteOnMerge,
                    @NotNull final List<GitRepository> repositories, @Nullable final Consumer<Boolean> resultHandler) {
    new CommonBackgroundTask(myProject, "Merging " + branchName, null) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        doMerge(branchName, deleteOnMerge, repositories, resultHandler, indicator);
      }
    }.runInBackground();
  }

  private void doMerge(@NotNull String branchName, DeleteOnMergeOption deleteOnMerge, @NotNull List<GitRepository> repositories,
                       @Nullable Consumer<Boolean> resultHandler, @NotNull ProgressIndicator indicator) {
    Map<GitRepository, String> revisions = new HashMap<GitRepository, String>();
    for (GitRepository repository : repositories) {
      revisions.put(repository, repository.getCurrentRevision());
    }
    new GitMergeOperation(myProject, myGit, repositories, branchName, deleteOnMerge, getCurrentBranchOrRev(repositories),
                          revisions, resultHandler, indicator).execute();
  }

  /**
   * Executes common operations before/after executing the actual branch operation.
   */
  private static abstract class CommonBackgroundTask extends Task.Backgroundable {

    @Nullable private final Runnable myCallInAwtAfterExecution;

    private CommonBackgroundTask(@Nullable final Project project, @NotNull final String title, @Nullable Runnable callInAwtAfterExecution) {
      super(project, title);
      myCallInAwtAfterExecution = callInAwtAfterExecution;
    }

    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      execute(indicator);
      if (myCallInAwtAfterExecution != null) {
        SwingUtilities.invokeLater(myCallInAwtAfterExecution);
      }
    }

    abstract void execute(@NotNull ProgressIndicator indicator);

    void runInBackground() {
      GitVcs.runInBackground(this);
    }

  }

}
