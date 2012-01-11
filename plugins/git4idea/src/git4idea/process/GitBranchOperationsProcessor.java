/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.process;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.UIUtil;
import git4idea.Git;
import git4idea.GitExecutionException;
import git4idea.GitVcs;
import git4idea.commands.GitCommandResult;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.GitCommit;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.stash.GitChangesSaver;
import git4idea.ui.branch.GitBranchUiUtil;
import git4idea.ui.branch.GitCompareBranchesDialog;
import git4idea.update.GitComplexProcess;
import git4idea.util.UntrackedFilesNotifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.ui.GitUIUtil.notifyError;

/**
 * Executor of Git branching operations.
 *
 * @author Kirill Likhodedov
 */
public final class GitBranchOperationsProcessor {

  private static final Logger LOG = Logger.getInstance(GitBranchOperationsProcessor.class);

  private final Project myProject;
  private final GitRepository myRepository;
  @Nullable private final Runnable myCallInAwtAfterExecution;

  public GitBranchOperationsProcessor(@NotNull GitRepository repository) {
    this(repository, null);
  }
  
  public GitBranchOperationsProcessor(@NotNull GitRepository repository, @Nullable Runnable callInAwtAfterExecution) {
    myProject = repository.getProject();
    myRepository = repository;
    myCallInAwtAfterExecution = callInAwtAfterExecution;
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
        doCheckoutNewBranch(name);
      }
    }.runInBackground();
  }

  public void createNewTag(@NotNull final String name, final String reference) {
    new CommonBackgroundTask(myProject, "Checking out new branch " + name, myCallInAwtAfterExecution) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        Git.createNewTag(myRepository, name, null, reference);
      }
    }.runInBackground();
  }

  private void doCheckoutNewBranch(@NotNull final String name) {
    GitSimpleEventDetector unmergedDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED);
    GitCommandResult result = Git.checkoutNewBranch(myRepository, name, unmergedDetector);
    if (result.success()) {
      updateRepository();
      notifySuccess(String.format("Branch <b><code>%s</code></b> was created", name));
    } else if (unmergedDetector.hasHappened()) {
      GitConflictResolver gitConflictResolver = prepareConflictResolverForUnmergedFilesBeforeCheckout();
      if (gitConflictResolver.merge()) { // try again to checkout
        doCheckoutNewBranch(name);
      }
    } else { // other error
      showErrorMessage("Couldn't create new branch " + name, result.getErrorOutput());
    }
  }

  private GitConflictResolver prepareConflictResolverForUnmergedFilesBeforeCheckout() {
    GitConflictResolver.Params params = new GitConflictResolver.Params().
      setMergeDescription("The following files have unresolved conflicts. You need to resolve them before checking out.").
      setErrorNotificationTitle("Can't create new branch");
    return new GitConflictResolver(myProject, Collections.singleton(myRepository.getRoot()), params);
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
  public void checkoutNewBranchStartingFrom(@NotNull String newBranchName,
                                            @NotNull String startPoint) {
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
    final GitMessageWithFilesDetector checkoutListener = new GitMessageWithFilesDetector(GitMessageWithFilesDetector.Event.LOCAL_CHANGES_OVERWRITTEN_BY_CHECKOUT, myRepository.getRoot());
    GitSimpleEventDetector unmergedDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED);
    GitMessageWithFilesDetector untrackedOverwrittenByCheckout = new GitMessageWithFilesDetector(GitMessageWithFilesDetector.Event.UNTRACKED_FILES_OVERWRITTEN_BY, myRepository.getRoot());

    GitCommandResult result = Git.checkout(myRepository, reference, newBranch, checkoutListener, unmergedDetector, untrackedOverwrittenByCheckout);
    if (result.success()) {
      refreshRoot();
      updateRepository();
      if (newBranch == null) {
        notifySuccess(String.format("Checked out <b><code>%s</code></b>", reference));
      } else {
        notifySuccess(String.format("Checked out new branch <b><code>%s</code></b> from <b><code>%s</code></b>", newBranch, reference));
      }
    }
    else if (unmergedDetector.hasHappened()) {
      GitConflictResolver gitConflictResolver = prepareConflictResolverForUnmergedFilesBeforeCheckout();
      if (gitConflictResolver.merge()) { // try again to checkout
        doCheckout(indicator, reference, newBranch);
      }
    }
    else if (checkoutListener.wasMessageDetected()) {
      List<Change> affectedChanges = getChangesAffectedByCheckout(checkoutListener.getRelativeFilePaths());
      if (GitWouldBeOverwrittenByCheckoutDialog.showAndGetAnswer(myProject, affectedChanges)) {
        smartCheckout(reference, newBranch, indicator);
      }
    }
    else if (untrackedOverwrittenByCheckout.wasMessageDetected()) {
      LOG.info("doCheckout: untracked files would be overwritten by checkout");
      UntrackedFilesNotifier.notifyUntrackedFilesOverwrittenBy(myProject, untrackedOverwrittenByCheckout.getFiles(), "checkout");
    }
    else {
      showErrorMessage("Couldn't checkout " + reference, result.getErrorOutput());
    }
  }

  // stash - checkout - unstash
  private void smartCheckout(@NotNull final String reference, @Nullable final String newBranch, @NotNull ProgressIndicator indicator) {
    final GitChangesSaver saver = configureSaver(reference, indicator);

    GitComplexProcess.Operation checkoutOperation = new GitComplexProcess.Operation() {
      @Override public void run(ContinuationContext context) {
        if (saveOrNotify(saver)) {
          try {
            checkoutOrNotify(reference, newBranch);
          } finally {
            saver.restoreLocalChanges(context);
          }
        }
      }
    };
    GitComplexProcess.execute(myProject, "checkout", checkoutOperation);
  }

  /**
   * Configures the saver, actually notifications and texts in the GitConflictResolver used inside.
   */
  private GitChangesSaver configureSaver(final String reference, ProgressIndicator indicator) {
    GitChangesSaver saver = GitChangesSaver.getSaver(myProject, indicator, String.format("Checkout %s at %s",
                                                                                         reference,
                                                                                         DateFormatUtil.formatDateTime(Clock.getTime())));
    MergeDialogCustomizer mergeDialogCustomizer = new MergeDialogCustomizer() {
      @Override
      public String getMultipleFileMergeDescription(Collection<VirtualFile> files) {
        return String.format(
          "<html>Uncommitted changes that were saved before checkout have conflicts with files from <code>%s</code></html>",
          reference);
      }

      @Override
      public String getLeftPanelTitle(VirtualFile file) {
        return "Uncommitted changes";
      }

      @Override
      public String getRightPanelTitle(VirtualFile file, VcsRevisionNumber lastRevisionNumber) {
        return String.format("<html>Changes from <b><code>%s</code></b></html>", reference);
      }
    };

    GitConflictResolver.Params params = new GitConflictResolver.Params().
      setReverse(true).
      setMergeDialogCustomizer(mergeDialogCustomizer).
      setErrorNotificationTitle("Local changes were not restored");

    saver.setConflictResolverParams(params);
    return saver;
  }

  /**
   * Saves local changes. In case of error shows a notification and returns false.
   */
  private boolean saveOrNotify(GitChangesSaver saver) {
    try {
      saver.saveLocalChanges(Collections.singleton(myRepository.getRoot()));
      return true;
    } catch (VcsException e) {
      LOG.info("Couldn't save local changes", e);
      notifyError(myProject, "Git checkout failed",
                  "Tried to save uncommitted changes in " + saver.getSaverName() + " before checkout, but failed with an error.<br/>" +
                  "Update was cancelled.", true, e);
      return false;
    }
  }

  /**
   * Checks out or shows an error message.
   */
  private boolean checkoutOrNotify(@NotNull String reference, @Nullable String newBranch) {
    GitCommandResult checkoutResult = Git.checkout(myRepository, reference, newBranch);
    if (checkoutResult.success()) {
      return true;
    }
    else {
      showErrorMessage("Couldn't checkout " + reference, checkoutResult.getErrorOutput());
      return false;
    }
  }

  /**
   * Forms the list of the changes, that would be overwritten by checkout.
   * @param affectedRelativePaths paths returned by Git.
   * @return List of Changes is these paths.
   */
  private List<Change> getChangesAffectedByCheckout(Set<String> affectedRelativePaths) {
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    List<Change> affectedChanges = new ArrayList<Change>();
    for (String relPath : affectedRelativePaths) {
      VirtualFile file = myRepository.getRoot().findFileByRelativePath(FileUtil.toSystemIndependentName(relPath));
      if (file != null) {
        Change change = changeListManager.getChange(file);
        if (change != null) {
          affectedChanges.add(change);
        }
      }
    }
    return affectedChanges;
  }

  private void refreshRoot() {
    myRepository.getRoot().refresh(true, true);
  }

  public void deleteBranch(final String branchName) {
    new CommonBackgroundTask(myProject, "Deleting " + branchName, myCallInAwtAfterExecution) {
      @Override public void execute(@NotNull ProgressIndicator indicator) {
        doDelete(branchName);
      }
    }.runInBackground();
  }

  private void doDelete(final String branchName) {
    GitSimpleEventDetector notFullyMergedDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.BRANCH_NOT_FULLY_MERGED);
    GitCommandResult result = Git.branchDelete(myRepository, branchName, false, notFullyMergedDetector);
    if (result.success()) {
      notifyBranchDeleteSuccess(branchName);
    } else if (notFullyMergedDetector.hasHappened()) {
      boolean forceDelete = showNotFullyMergedDialog(branchName);
      if (forceDelete) {
        doForceDelete(branchName);
      }
    } else {
      showErrorMessage("Couldn't delete " + branchName, result.getErrorOutput());
    }
  }

  /**
   * Shows a dialog "the branch is not fully merged" with the list of commits.
   * User may still want to force delete the branch.
   * @return true if the branch should be force deleted.
   */
  private boolean showNotFullyMergedDialog(@NotNull final String branchName) {
    final List<String> mergedToBranches = getMergedToBranches(branchName);

    final List<GitCommit> history;
    try {
      history = GitHistoryUtils.history(myProject, myRepository.getRoot(), ".." + branchName);
    } catch (VcsException e) {
      // this is critical, because we need to show the list of unmerged commits, and it shouldn't happen => inform user and developer
      throw new GitExecutionException("Couldn't get [git log .." + branchName + "] on repository [" + myRepository.getRoot() + "]", e);
    }

    final AtomicBoolean forceDelete = new AtomicBoolean();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override public void run() {
        forceDelete.set(GitBranchIsNotFullyMergedDialog.showAndGetAnswer(myProject, history, myRepository, branchName, mergedToBranches));
      }
    });
    return forceDelete.get();
  }

  /**
   * Branches which the given branch is merged to ({@code git branch --merged},
   * except the given branch itself.
   */
  private List<String> getMergedToBranches(@NotNull String branchName) {
    String tip = tip(branchName);
    if (tip == null) {
      return Collections.emptyList();
    }
    return branchContainsCommit(tip, branchName);
  }

  @Nullable
  private String tip(@NotNull String branchName) {
    GitCommandResult result = Git.tip(myRepository, branchName);
    if (result.success() && result.getOutput().size() == 1) {
      return result.getOutput().get(0).trim();
    }
    // failing in this method is not critical - it is just additional information. So we just log the error
    LOG.info("Failed to get [git rev-list -1] for branch [" + branchName + "]. " + result);
    return null;
  }

  private List<String> branchContainsCommit(@NotNull String tip, @NotNull String branchName) {
    GitCommandResult result = Git.branchContains(myRepository, tip);
    if (result.success()) {
      List<String> branches = new ArrayList<String>();
      for (String s : result.getOutput()) {
        s = s.trim();
        if (s.startsWith("*")) {
          s = s.substring(2);
        }
        if (!s.equals(branchName)) { // this branch contains itself - not interesting
          branches.add(s);
        }
      }
      return branches;
    } 
      
    // failing in this method is not critical - it is just additional information. So we just log the error
    LOG.info("Failed to get [git branch --contains] for hash [" + tip + "]. " + result);
    return Collections.emptyList();
  }

  private void notifyBranchDeleteSuccess(String branchName) {
    updateRepository();
    notifySuccess(String.format("Deleted branch <b><code>%s</code></b>", branchName));
  }

  private void doForceDelete(@NotNull String branchName) {
    GitCommandResult res = Git.branchDelete(myRepository, branchName, true);
    if (res.success()) {
      notifyBranchDeleteSuccess(branchName);
    } else {
      showErrorMessage("Couldn't delete " + branchName, res.getErrorOutput());
    }
  }

  /**
   * Compares the HEAD with the specified branch - shows a dialog with the differences.
   * @param branchName name of the branch to compare with.
   */
  public void compare(@NotNull final String branchName) {
    new CommonBackgroundTask(myProject, "Comparing with " + branchName, myCallInAwtAfterExecution) {

      private Pair<List<GitCommit>,List<GitCommit>> myCommits;

      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        myCommits = loadCommitsToCompare(branchName);
      }

      @Override
      public void onSuccess() {
        displayCompareDialog(myCommits.getFirst(), myCommits.getSecond(), branchName);
      }
    }.runInBackground();
  }

  private Pair<List<GitCommit>, List<GitCommit>> loadCommitsToCompare(@NotNull final String branchName) {
    final List<GitCommit> headToBranch;
    final List<GitCommit> branchToHead;
    try {
      headToBranch = GitHistoryUtils.history(myProject, myRepository.getRoot(), ".." + branchName);
      branchToHead = GitHistoryUtils.history(myProject, myRepository.getRoot(), branchName + "..");
    }
    catch (VcsException e) {
      // we treat it as critical and report an error
      throw new GitExecutionException("Couldn't get [git log .." + branchName + "] on repository [" + myRepository.getRoot() + "]", e);
    }
    return Pair.create(headToBranch, branchToHead);
  }

  private void displayCompareDialog(List<GitCommit> headToBranch, List<GitCommit> branchToHead, String branchName) {
    if (headToBranch.isEmpty() && branchToHead.isEmpty()) {
      String currentBranch = GitBranchUiUtil.getBranchNameOrRev(myRepository);
      Messages.showInfoMessage(myProject, String.format("<html>There are no changes between <code>%s</code> and <code>%s</code></html>",
                                                        currentBranch, branchName), "No Changes Detected");
    } else {
      new GitCompareBranchesDialog(myRepository, branchName, headToBranch, branchToHead).show();
    }
  }

  private void updateRepository() {
    myRepository.update(GitRepository.TrackedTopic.CURRENT_BRANCH, GitRepository.TrackedTopic.BRANCHES);
  }
  
  private void showErrorMessage(@NotNull final String message, @NotNull final List<String> errorOutput) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        Messages.showErrorDialog(myProject, StringUtil.join(errorOutput, "\n"), message);
      }
    });
  }
  
  private void notifySuccess(String message) {
    GitVcs.NOTIFICATION_GROUP_ID.createNotification(message, NotificationType.INFORMATION).notify(myProject);
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
