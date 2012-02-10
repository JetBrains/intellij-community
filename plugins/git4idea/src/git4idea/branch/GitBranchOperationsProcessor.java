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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.ui.UIUtil;
import git4idea.GitBranch;
import git4idea.GitExecutionException;
import git4idea.GitVcs;
import git4idea.NotificationManager;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
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
 * Executor of Git branching operations.
 *
 * @author Kirill Likhodedov
 */
public final class GitBranchOperationsProcessor {

  private static final Logger LOG = Logger.getInstance(GitBranchOperationsProcessor.class);

  private final Project myProject;
  private final List<GitRepository> myRepositories;
  @Nullable private final Runnable myCallInAwtAfterExecution;
  private final GitRepository mySelectedRepository;

  public GitBranchOperationsProcessor(@NotNull GitRepository repository) {
    this(repository, null);
  }
  
  public GitBranchOperationsProcessor(@NotNull GitRepository repository, @Nullable Runnable callInAwtAfterExecution) {
    this(repository.getProject(), Collections.singletonList(repository), repository, callInAwtAfterExecution);
  }

  public GitBranchOperationsProcessor(@NotNull Project project, @NotNull List<GitRepository> repositories,
                                      @NotNull GitRepository selectedRepository) {
    this(project, repositories, selectedRepository, null);
  }

  public GitBranchOperationsProcessor(@NotNull Project project,
                                      @NotNull List<GitRepository> repositories,
                                      @NotNull GitRepository selectedRepository,
                                      @Nullable Runnable callInAwtAfterExecution) {
    myProject = project;
    myRepositories = repositories;
    mySelectedRepository = selectedRepository;
    myCallInAwtAfterExecution = callInAwtAfterExecution;
  }
  
  @NotNull
  private String getCurrentBranchOrRev() {
    if (myRepositories.size() > 1) {
      GitMultiRootBranchConfig multiRootBranchConfig = new GitMultiRootBranchConfig(myRepositories);
      String currentBranch = multiRootBranchConfig.getCurrentBranch();
      LOG.assertTrue(currentBranch != null, "Repositories have unexpectedly diverged. " + multiRootBranchConfig);
      return currentBranch;
    }
    else {
      assert !myRepositories.isEmpty() : "No repositories passed to GitBranchOperationsProcessor.";
      GitRepository repository = myRepositories.iterator().next();
      return GitBranchUiUtil.getBranchNameOrRev(repository);
    }
  }

  /**
   * Checks out a new branch in background.
   * If there are unmerged files, proposes to resolve the conflicts and tries to check out again.
   * Doesn't check the name of new branch for validity - do this before calling this method, otherwise a standard error dialog will be shown.
   *
   * @param name Name of the new branch to check out.
   */
  public void checkoutNewBranch(@NotNull final String name) {
    new CommonBackgroundTask(myProject, "Checking out new branch " + name, myCallInAwtAfterExecution) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        doCheckoutNewBranch(name, indicator);
      }
    }.runInBackground();
  }

  public void createNewTag(@NotNull final String name, final String reference) {
    new CommonBackgroundTask(myProject, "Checking out new branch " + name, myCallInAwtAfterExecution) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        for (GitRepository repository : myRepositories) {
          Git.createNewTag(repository, name, null, reference);
        }
      }
    }.runInBackground();
  }

  private void doCheckoutNewBranch(@NotNull final String name, @NotNull ProgressIndicator indicator) {
    new GitCheckoutNewBranchOperation(myProject, myRepositories, name, getCurrentBranchOrRev(), indicator).execute();
  }

  /**
   * Creates and checks out a new local branch starting from the given reference:
   * {@code git checkout -b <branchname> <start-point>}. <br/>
   * If the reference is a remote branch, and the tracking is wanted, pass {@code true} in the "track" parameter.
   * Provides the "smart checkout" procedure the same as in {@link #checkout(String)}.
   *
   * @param newBranchName     Name of new local branch.
   * @param startPoint        Reference to checkout.
   */
  public void checkoutNewBranchStartingFrom(@NotNull String newBranchName, @NotNull String startPoint) {
    commonCheckout(startPoint, newBranchName);
  }

  /**
   * <p>
   *   Checks out the given reference (a branch, or a reference name, or a commit hash).
   *   If local changes prevent the checkout, shows the list of them and proposes to make a "smart checkout":
   *   stash-checkout-unstash.
   * </p>
   * <p>
   *   Doesn't check the reference for validity.
   * </p>
   *
   * @param reference reference to be checked out.
   */
  public void checkout(@NotNull final String reference) {
    commonCheckout(reference, null);
  }

  private void commonCheckout(@NotNull final String reference, @Nullable final String newBranch) {
    new CommonBackgroundTask(myProject, "Checking out " + reference, myCallInAwtAfterExecution) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        doCheckout(indicator, reference, newBranch);
      }
    }.runInBackground();
  }

  private void doCheckout(@NotNull ProgressIndicator indicator, @NotNull String reference, @Nullable String newBranch) {
    new GitCheckoutOperation(myProject, myRepositories, reference, newBranch, getCurrentBranchOrRev(), indicator).execute();
  }

  public void deleteBranch(final String branchName) {
    new CommonBackgroundTask(myProject, "Deleting " + branchName, myCallInAwtAfterExecution) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        new GitDeleteBranchOperation(myProject, myRepositories, branchName, getCurrentBranchOrRev(), indicator).execute();
      }
    }.runInBackground();
  }

  public void deleteRemoteBranch(@NotNull final String branchName) {
    final Collection<String> trackingBranches = findTrackingBranches(branchName);
    String currentBranch = getCurrentBranchOrRev();
    boolean currentBranchTracksBranchToDelete = false;
    if (trackingBranches.contains(currentBranch)) {
      currentBranchTracksBranchToDelete = true;
      trackingBranches.remove(currentBranch);
    }

    final DeleteRemoteBranchDecision decision = confirmBranchDeletion(branchName, trackingBranches, currentBranchTracksBranchToDelete);

    if (decision.delete()) {
      new CommonBackgroundTask(myProject, "Deleting " + branchName, myCallInAwtAfterExecution) {
        @Override public void execute(@NotNull ProgressIndicator indicator) {
          boolean deletedSuccessfully = doDeleteRemote(branchName);
          if (deletedSuccessfully) {
            final Collection<String> successfullyDeletedLocalBranches = new ArrayList<String>(1);
            if (decision.deleteTracking()) {
              for (final String branch : trackingBranches) {
                indicator.setText("Deleting " + branch);
                new GitDeleteBranchOperation(myProject, myRepositories, branch, getCurrentBranchOrRev(), indicator) {
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
  private Collection<String> findTrackingBranches(@NotNull String remoteBranch) {
    return new GitMultiRootBranchConfig(myRepositories).getTrackingBranches(remoteBranch);
  }

  private boolean doDeleteRemote(String branchName) {
    GitCompoundResult result = new GitCompoundResult(myProject);
    for (GitRepository repository : myRepositories) {
      Pair<String, String> pair = GitBranch.splitNameOfRemoteBranch(branchName);
      GitCommandResult res = Git.push(repository, pair.getFirst(), ":" + pair.getSecond());
      result.append(repository, res);
      repository.update(GitRepository.TrackedTopic.BRANCHES);
    }
    if (!result.totalSuccess()) {
      NotificationManager.getInstance(myProject).notifyError("Failed to delete remote branch " + branchName,
                                                             result.getErrorOutputWithReposIndication());
    }
    return result.totalSuccess();
  }

  private void notifySuccessfulDeletion(@NotNull String remoteBranchName, @NotNull Collection<String> localBranches) {
    String message = "";
    if (!localBranches.isEmpty()) {
      message = "Also deleted local " + StringUtil.pluralize("branch", localBranches.size()) + ": " + StringUtil.join(localBranches, ", ");
    }
    NotificationManager.getInstance(myProject).notify(GitVcs.NOTIFICATION_GROUP_ID, "Deleted remote branch " + remoteBranchName,
                                                      message, NotificationType.INFORMATION);
  }

  private DeleteRemoteBranchDecision confirmBranchDeletion(@NotNull String branchName, @NotNull Collection<String> trackingBranches,
                                                           boolean currentBranchTracksBranchToDelete) {
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
        message += "\n\nCurrent branch " + getCurrentBranchOrRev() + " tracks " + branchName + " but won't be deleted.";
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

  /**
   * Compares the HEAD with the specified branch - shows a dialog with the differences.
   * @param branchName name of the branch to compare with.
   */
  public void compare(@NotNull final String branchName) {
    new CommonBackgroundTask(myProject, "Comparing with " + branchName, myCallInAwtAfterExecution) {
  
      private GitCommitCompareInfo myCompareInfo;
  
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        myCompareInfo = loadCommitsToCompare(myRepositories, branchName);
      }
  
      @Override
      public void onSuccess() {
        if (myCompareInfo == null) {
          LOG.error("The task to get compare info didn't finish. Repositories: \n" + myRepositories + "\nbranch name: " + branchName);
          return;
        }
        displayCompareDialog(branchName, getCurrentBranchOrRev(), myCompareInfo);
      }
    }.runInBackground();
  }

  private GitCommitCompareInfo loadCommitsToCompare(List<GitRepository> repositories, String branchName) {
    GitCommitCompareInfo compareInfo = new GitCommitCompareInfo();
    for (GitRepository repository : repositories) {
      compareInfo.put(repository, loadCommitsToCompare(repository, branchName));
    }
    return compareInfo;
  }
  
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
  
  private void displayCompareDialog(@NotNull String branchName, @NotNull String currentBranch, @NotNull GitCommitCompareInfo compareInfo) {
    if (compareInfo.isEmpty()) {
      Messages.showInfoMessage(myProject, String.format("<html>There are no changes between <code>%s</code> and <code>%s</code></html>",
                                                        currentBranch, branchName), "No Changes Detected");
    }
    else {
      new GitCompareBranchesDialog(myProject, branchName, currentBranch, compareInfo, mySelectedRepository).show();
    }
  }

  public void merge(@NotNull final String branchName) {
    new CommonBackgroundTask(myProject, "Merging " + branchName, myCallInAwtAfterExecution) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        doMerge(branchName, indicator);
      }
    }.runInBackground();
  }

  private void doMerge(@NotNull String branchName, @NotNull ProgressIndicator indicator) {
    Map<GitRepository, String> revisions = new HashMap<GitRepository, String>();
    for (GitRepository repository : myRepositories) {
      revisions.put(repository, repository.getCurrentRevision());
    }
    new GitMergeOperation(myProject, myRepositories, branchName, getCurrentBranchOrRev(), mySelectedRepository, revisions, indicator).execute();
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
      saveAllDocuments();
      execute(indicator);
      if (myCallInAwtAfterExecution != null) {
        SwingUtilities.invokeLater(myCallInAwtAfterExecution);
      }
    }

    abstract void execute(@NotNull ProgressIndicator indicator);

    void runInBackground() {
      GitVcs.runInBackground(this);
    }

    private static void saveAllDocuments() {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          FileDocumentManager.getInstance().saveAllDocuments();
        }
      });
    }
  }

}
