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

import com.intellij.dvcs.DvcsUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import git4idea.GitUtil;
import git4idea.commands.*;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.util.GitPreservingProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.util.GitUIUtil.code;

/**
 * Represents {@code git checkout} operation.
 * Fails to checkout if there are unmerged files.
 * Fails to checkout if there are untracked files that would be overwritten by checkout. Shows the list of files.
 * If there are local changes that would be overwritten by checkout, proposes to perform a "smart checkout" which means stashing local
 * changes, checking out, and then unstashing the changes back (possibly with showing the conflict resolving dialog). 
 *
 *  @author Kirill Likhodedov
 */
class GitCheckoutOperation extends GitBranchOperation {

  public static final String ROLLBACK_PROPOSAL_FORMAT = "You may rollback (checkout back to previous branch) not to let branches diverge.";

  @NotNull private final String myStartPointReference;
  private final boolean myDetach;
  private final boolean myRefShouldBeValid;
  @Nullable private final String myNewBranch;

  GitCheckoutOperation(@NotNull Project project,
                       @NotNull Git git,
                       @NotNull GitBranchUiHandler uiHandler,
                       @NotNull Collection<GitRepository> repositories,
                       @NotNull String startPointReference,
                       boolean detach,
                       boolean refShouldBeValid,
                       @Nullable String newBranch) {
    super(project, git, uiHandler, repositories);
    myStartPointReference = startPointReference;
    myDetach = detach;
    myRefShouldBeValid = refShouldBeValid;
    myNewBranch = newBranch;
  }
  
  @Override
  protected void execute() {
    saveAllDocuments();
    boolean fatalErrorHappened = false;
    AccessToken token = DvcsUtil.workingTreeChangeStarted(myProject);
    try {
      while (hasMoreRepositories() && !fatalErrorHappened) {
        final GitRepository repository = next();

        VirtualFile root = repository.getRoot();
        GitLocalChangesWouldBeOverwrittenDetector localChangesDetector =
          new GitLocalChangesWouldBeOverwrittenDetector(root, GitLocalChangesWouldBeOverwrittenDetector.Operation.CHECKOUT);
        GitSimpleEventDetector unmergedFiles = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED_PREVENTING_CHECKOUT);
        GitSimpleEventDetector unknownPathspec = new GitSimpleEventDetector(GitSimpleEventDetector.Event.INVALID_REFERENCE);
        GitUntrackedFilesOverwrittenByOperationDetector untrackedOverwrittenByCheckout =
          new GitUntrackedFilesOverwrittenByOperationDetector(root);

        GitCommandResult result = myGit.checkout(repository, myStartPointReference, myNewBranch, false, myDetach,
                                                 localChangesDetector, unmergedFiles, unknownPathspec, untrackedOverwrittenByCheckout);
        if (result.success()) {
          refresh(repository);
          markSuccessful(repository);
        }
        else if (unmergedFiles.hasHappened()) {
          fatalUnmergedFilesError();
          fatalErrorHappened = true;
        }
        else if (localChangesDetector.wasMessageDetected()) {
          boolean smartCheckoutSucceeded = smartCheckoutOrNotify(repository, localChangesDetector);
          if (!smartCheckoutSucceeded) {
            fatalErrorHappened = true;
          }
        }
        else if (untrackedOverwrittenByCheckout.wasMessageDetected()) {
          fatalUntrackedFilesError(repository.getRoot(), untrackedOverwrittenByCheckout.getRelativeFilePaths());
          fatalErrorHappened = true;
        }
        else if (!myRefShouldBeValid && unknownPathspec.hasHappened()) {
          markSkip(repository);
        }
        else {
          fatalError(getCommonErrorTitle(), result.getErrorOutputAsJoinedString());
          fatalErrorHappened = true;
        }
      }
    }
    finally {
      DvcsUtil.workingTreeChangeFinished(myProject, token);
    }

    if (!fatalErrorHappened) {
      if (wereSuccessful()) {
        if (!wereSkipped()) {
          notifySuccess();
          updateRecentBranch();
        }
        else {
          String mentionSuccess = getSuccessMessage() + GitUtil.mention(getSuccessfulRepositories(), 4);
          String mentionSkipped = wereSkipped() ? "<br>Revision not found" + GitUtil.mention(getSkippedRepositories(), 4) : "";

          VcsNotifier.getInstance(myProject).notifySuccess("",
                                                           mentionSuccess +
                                                           mentionSkipped +
                                                           "<br><a href='rollback'>Rollback</a>",
                                                           new RollbackOperationNotificationListener());
          updateRecentBranch();
        }
      }
      else {
        LOG.assertTrue(!myRefShouldBeValid);
        notifyError("Couldn't checkout " + myStartPointReference, "Revision not found" + GitUtil.mention(getSkippedRepositories(), 4));
      }
    }
  }

  private boolean smartCheckoutOrNotify(@NotNull GitRepository repository,
                                        @NotNull GitMessageWithFilesDetector localChangesOverwrittenByCheckout) {
    Pair<List<GitRepository>, List<Change>> conflictingRepositoriesAndAffectedChanges =
      getConflictingRepositoriesAndAffectedChanges(repository, localChangesOverwrittenByCheckout, myCurrentHeads.get(repository),
                                                   myStartPointReference);
    List<GitRepository> allConflictingRepositories = conflictingRepositoriesAndAffectedChanges.getFirst();
    List<Change> affectedChanges = conflictingRepositoriesAndAffectedChanges.getSecond();

    Collection<String> absolutePaths = GitUtil.toAbsolute(repository.getRoot(), localChangesOverwrittenByCheckout.getRelativeFilePaths());
    int smartCheckoutDecision = myUiHandler.showSmartOperationDialog(myProject, affectedChanges, absolutePaths, "checkout",
                                                                     "&Force Checkout");
    if (smartCheckoutDecision == GitSmartOperationDialog.SMART_EXIT_CODE) {
      boolean smartCheckedOutSuccessfully = smartCheckout(allConflictingRepositories, myStartPointReference, myNewBranch, getIndicator());
      if (smartCheckedOutSuccessfully) {
        for (GitRepository conflictingRepository : allConflictingRepositories) {
          markSuccessful(conflictingRepository);
          refresh(conflictingRepository);
        }
        return true;
      }
      else {
        // notification is handled in smartCheckout()
        return false;
      }
    }
    else if (smartCheckoutDecision == GitSmartOperationDialog.FORCE_EXIT_CODE) {
      boolean forceCheckoutSucceeded = checkoutOrNotify(allConflictingRepositories, myStartPointReference, myNewBranch, true);
      if (forceCheckoutSucceeded) {
        markSuccessful(ArrayUtil.toObjectArray(allConflictingRepositories, GitRepository.class));
        refresh(ArrayUtil.toObjectArray(allConflictingRepositories, GitRepository.class));
      }
      return forceCheckoutSucceeded;
    }
    else {
      fatalLocalChangesError(myStartPointReference);
      return false;
    }
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    return "However checkout has succeeded for the following " + repositories() + ":<br/>" +
           successfulRepositoriesJoined() + "<br/>" + ROLLBACK_PROPOSAL_FORMAT;
  }

  @NotNull
  @Override
  protected String getOperationName() {
    return "checkout";
  }

  @Override
  protected void rollback() {
    GitCompoundResult checkoutResult = new GitCompoundResult(myProject);
    GitCompoundResult deleteResult = new GitCompoundResult(myProject);
    for (GitRepository repository : getSuccessfulRepositories()) {
      GitCommandResult result = myGit.checkout(repository, myCurrentHeads.get(repository), null, true, false);
      checkoutResult.append(repository, result);
      if (result.success() && myNewBranch != null) {
        /*
          force delete is needed, because we create new branch from branch other that the current one
          e.g. being on master create newBranch from feature,
          then rollback => newBranch is not fully merged to master (although it is obviously fully merged to feature).
         */
        deleteResult.append(repository, myGit.branchDelete(repository, myNewBranch, true));
      }
      refresh(repository);
    }
    if (!checkoutResult.totalSuccess() || !deleteResult.totalSuccess()) {
      StringBuilder message = new StringBuilder();
      if (!checkoutResult.totalSuccess()) {
        message.append("Errors during checkout: ");
        message.append(checkoutResult.getErrorOutputWithReposIndication());
      }
      if (!deleteResult.totalSuccess()) {
        message.append("Errors during deleting ").append(code(myNewBranch)).append(": ");
        message.append(deleteResult.getErrorOutputWithReposIndication());
      }
      VcsNotifier.getInstance(myProject).notifyError("Error during rollback",
                                                     message.toString());
    }
  }

  @NotNull
  private String getCommonErrorTitle() {
    return "Couldn't checkout " + myStartPointReference;
  }

  @NotNull
  @Override
  public String getSuccessMessage() {
    if (myNewBranch == null) {
      return String.format("Checked out <b><code>%s</code></b>", myStartPointReference);
    }
    return String.format("Checked out new branch <b><code>%s</code></b> from <b><code>%s</code></b>", myNewBranch, myStartPointReference);
  }

  // stash - checkout - unstash
  private boolean smartCheckout(@NotNull final List<GitRepository> repositories, @NotNull final String reference,
                                @Nullable final String newBranch, @NotNull ProgressIndicator indicator) {
    final AtomicBoolean result = new AtomicBoolean();
    GitPreservingProcess preservingProcess = new GitPreservingProcess(myProject, myGit,
                                                                      GitUtil.getRootsFromRepositories(repositories), "checkout", reference,
                                                                      GitVcsSettings.UpdateChangesPolicy.STASH, indicator,
                                                                      new Runnable() {
      @Override
      public void run() {
        result.set(checkoutOrNotify(repositories, reference, newBranch, false));
      }
    });
    preservingProcess.execute();
    return result.get();
  }

  /**
   * Checks out or shows an error message.
   */
  private boolean checkoutOrNotify(@NotNull List<GitRepository> repositories, 
                                   @NotNull String reference, @Nullable String newBranch, boolean force) {
    GitCompoundResult compoundResult = new GitCompoundResult(myProject);
    for (GitRepository repository : repositories) {
      compoundResult.append(repository, myGit.checkout(repository, reference, newBranch, force, myDetach));
    }
    if (compoundResult.totalSuccess()) {
      return true;
    }
    notifyError("Couldn't checkout " + reference, compoundResult.getErrorOutputWithReposIndication());
    return false;
  }

  private void refresh(GitRepository... repositories) {
    for (GitRepository repository : repositories) {
      refreshRoot(repository);
      // repository state will be auto-updated with this VFS refresh => in general there is no need to call GitRepository#update()
      // but to avoid problems of the asynchronous refresh, let's force update the repository info.
      repository.update();
    }
  }

  private class RollbackOperationNotificationListener implements NotificationListener {
    @Override
    public void hyperlinkUpdate(@NotNull Notification notification,
                                @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equalsIgnoreCase("rollback")) {
        rollback();
      }
    }
  }
}
