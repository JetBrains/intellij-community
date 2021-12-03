// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import git4idea.GitProtectedBranchesKt;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.*;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.config.GitSharedSettings;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.util.GitPreservingProcess;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.dvcs.DvcsUtil.joinShortNames;
import static com.intellij.util.containers.UtilKt.getIfSingle;
import static git4idea.GitBranchesUsageCollector.*;
import static git4idea.GitNotificationIdsHolder.CHECKOUT_ROLLBACK_ERROR;
import static git4idea.GitNotificationIdsHolder.CHECKOUT_SUCCESS;
import static git4idea.GitUtil.*;
import static git4idea.branch.GitSmartOperationDialog.Choice.FORCE;
import static git4idea.branch.GitSmartOperationDialog.Choice.SMART;
import static git4idea.util.GitUIUtil.bold;
import static git4idea.util.GitUIUtil.code;

/**
 * Represents {@code git checkout} operation.
 * Fails to checkout if there are unmerged files.
 * Fails to checkout if there are untracked files that would be overwritten by checkout. Shows the list of files.
 * If there are local changes that would be overwritten by checkout, proposes to perform a "smart checkout" which means stashing local
 * changes, checking out, and then unstashing the changes back (possibly with showing the conflict resolving dialog).
 */
class GitCheckoutOperation extends GitBranchOperation {
  private static final int REPOSITORIES_LIMIT = 4;
  @NonNls private static final String ROLLBACK_HREF_ATTRIBUTE = "rollback";

  @NotNull private final String myStartPointReference;
  private final boolean myDetach;
  private final boolean myReset;
  private final boolean myRefShouldBeValid;
  @Nullable private final String myNewBranch;

  GitCheckoutOperation(@NotNull Project project,
                       @NotNull Git git,
                       @NotNull GitBranchUiHandler uiHandler,
                       @NotNull Collection<? extends GitRepository> repositories,
                       @NotNull String startPointReference,
                       boolean detach,
                       boolean withReset,
                       boolean refShouldBeValid,
                       @Nullable String newBranch) {
    super(project, git, uiHandler, repositories);
    myStartPointReference = startPointReference;
    myDetach = detach;
    myReset = withReset;
    myRefShouldBeValid = refShouldBeValid;
    myNewBranch = newBranch;
  }

  @Override
  protected void execute() {
    StructuredIdeActivity checkoutActivity = CHECKOUT_ACTIVITY.started(myProject, () -> List.of(
      IS_BRANCH_PROTECTED.with(isBranchProtected()),
      IS_NEW_BRANCH.with(myNewBranch != null)
    ));
    Ref<Boolean> finishedSuccessfullyRef = Ref.create(false);
    try {
      finishedSuccessfullyRef.set(doExecute(checkoutActivity));
    }
    finally {
      checkoutActivity.finished(() -> List.of(
        FINISHED_SUCCESSFULLY.with(finishedSuccessfullyRef.get())
      ));
    }
  }

  private boolean isBranchProtected() {
    GitSharedSettings sharedSettings = GitSharedSettings.getInstance(myProject);
    return sharedSettings.isBranchProtected(myStartPointReference) ||
           GitProtectedBranchesKt.isRemoteBranchProtected(getRepositories(), myStartPointReference);
  }

  private boolean doExecute(StructuredIdeActivity activity) {
    saveAllDocuments();
    boolean success = false;
    boolean fatalErrorHappened = false;
    notifyBranchWillChange();
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, getOperationName())) {
      while (hasMoreRepositories() && !fatalErrorHappened) {
        final GitRepository repository = next();
        VirtualFile root = repository.getRoot();

        Hash startHash = getHead(repository);

        GitLocalChangesWouldBeOverwrittenDetector localChangesDetector =
          new GitLocalChangesWouldBeOverwrittenDetector(root, GitLocalChangesWouldBeOverwrittenDetector.Operation.CHECKOUT);
        GitSimpleEventDetector unmergedFiles = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED_PREVENTING_CHECKOUT);
        GitSimpleEventDetector unknownPathspec = new GitSimpleEventDetector(GitSimpleEventDetector.Event.INVALID_REFERENCE);
        GitUntrackedFilesOverwrittenByOperationDetector untrackedOverwrittenByCheckout =
          new GitUntrackedFilesOverwrittenByOperationDetector(root);

        StructuredIdeActivity checkoutOperation = CHECKOUT_OPERATION.startedWithParent(myProject, activity);
        GitCommandResult result = myGit.checkout(repository, myStartPointReference, myNewBranch, false, myDetach, myReset,
                                                 localChangesDetector, unmergedFiles, unknownPathspec, untrackedOverwrittenByCheckout);
        checkoutOperation.finished();
        if (result.success()) {
          StructuredIdeActivity vfsRefresh = VFS_REFRESH.startedWithParent(myProject, activity);
          updateAndRefreshChangedVfs(repository, startHash);
          vfsRefresh.finished();
          markSuccessful(repository);
        }
        else if (unmergedFiles.hasHappened()) {
          fatalUnmergedFilesError();
          fatalErrorHappened = true;
        }
        else if (localChangesDetector.wasMessageDetected()) {
          boolean smartCheckoutSucceeded = smartCheckoutOrNotify(repository, localChangesDetector, activity);
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
          fatalError(getCommonErrorTitle(), result);
          fatalErrorHappened = true;
        }
      }
    }

    if (!fatalErrorHappened) {
      Collection<GitRepository> repositories = getSkippedRepositories();
      String revisionNotFound = GitBundle.message("checkout.operation.revision.not.found",
                                                  repositories.size(),
                                                  joinShortNames(repositories, REPOSITORIES_LIMIT));
      if (wereSuccessful()) {
        if (!wereSkipped()) {
          notifySuccess();
        }
        else {
          Collection<GitRepository> successfulRepositories = getSuccessfulRepositories();
          HtmlBuilder builder = new HtmlBuilder();
          String mentionSuccess = GitBundle.message("checkout.operation.in", getSuccessMessage(),
                                                    successfulRepositories.size(),
                                                    joinShortNames(successfulRepositories, REPOSITORIES_LIMIT));
          builder.appendRaw(mentionSuccess);
          if (wereSkipped()) {
            builder.br().append(revisionNotFound);
          }
          builder.br().appendLink(ROLLBACK_HREF_ATTRIBUTE, GitBundle.message("checkout.operation.rollback"));

          VcsNotifier.getInstance(myProject).notifySuccess(CHECKOUT_SUCCESS, "",
                                                           builder.toString(),
                                                           new RollbackOperationNotificationListener());
        }
        success = true;
        notifyBranchHasChanged(myStartPointReference);
        updateRecentBranch();
      }
      else {
        LOG.assertTrue(!myRefShouldBeValid);
        notifyError(GitBundle.message("checkout.operation.could.not.checkout.error", getRefPresentation(myStartPointReference)),
                    revisionNotFound);
      }
    }
    return success;
  }

  private boolean smartCheckoutOrNotify(@NotNull GitRepository repository,
                                        @NotNull GitMessageWithFilesDetector localChangesOverwrittenByCheckout,
                                        @NotNull StructuredIdeActivity activity) {
    Pair<List<GitRepository>, List<Change>> conflictingRepositoriesAndAffectedChanges =
      getConflictingRepositoriesAndAffectedChanges(repository, localChangesOverwrittenByCheckout, myCurrentHeads.get(repository),
                                                   myStartPointReference);
    List<GitRepository> allConflictingRepositories = conflictingRepositoriesAndAffectedChanges.getFirst();
    List<Change> affectedChanges = conflictingRepositoriesAndAffectedChanges.getSecond();

    Collection<String> absolutePaths = toAbsolute(repository.getRoot(), localChangesOverwrittenByCheckout.getRelativeFilePaths());

    //activity.stageWithDurationStarted(IN_UI);
    GitSmartOperationDialog.Choice decision = myUiHandler.showSmartOperationDialog(myProject, affectedChanges, absolutePaths,
                                                                                   GitBundle.message("checkout.operation.name"),
                                                                                   GitBundle.message("checkout.operation.force.checkout"));
    if (decision == SMART) {
      Hash startHash = getHead(repository);
      boolean smartCheckedOutSuccessfully
        = smartCheckout(allConflictingRepositories, myStartPointReference, myNewBranch, getIndicator(), activity);
      if (smartCheckedOutSuccessfully) {
        for (GitRepository conflictingRepository : allConflictingRepositories) {
          markSuccessful(conflictingRepository);
          StructuredIdeActivity vfsRefresh = VFS_REFRESH.startedWithParent(myProject, activity);
          updateAndRefreshChangedVfs(conflictingRepository, startHash);
          vfsRefresh.finished();
        }
        return true;
      }
      else {
        // notification is handled in smartCheckout()
        return false;
      }
    }
    else if (decision == FORCE) {
      Map<GitRepository, Collection<Change>> changesToRefresh = StreamEx.of(allConflictingRepositories).toMap(repo -> {
        return GitChangeUtils.getDiffWithWorkingTree(repo, myStartPointReference, false);
      });
      boolean forceCheckoutSucceeded = checkoutOrNotify(allConflictingRepositories, myStartPointReference, myNewBranch, true, activity);
      if (forceCheckoutSucceeded) {
        markSuccessful(allConflictingRepositories.toArray(new GitRepository[0]));
        updateRepositories(allConflictingRepositories);
        StructuredIdeActivity vfsRefresh = VFS_REFRESH.startedWithParent(myProject, activity);
        allConflictingRepositories.forEach(repo -> refreshVfs(repo.getRoot(), changesToRefresh.get(repo)));
        vfsRefresh.finished();
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
    Collection<GitRepository> repositories = getSuccessfulRepositories();
    String previousBranch = getIfSingle(repositories.stream().map(myCurrentHeads::get).distinct());
    if (previousBranch == null) previousBranch = GitBundle.message("checkout.operation.previous.branch");
    String rollBackProposal = GitBundle.message("checkout.operation.you.may.rollback.not.to.let.branches.diverge", previousBranch);
    return new HtmlBuilder()
      .append(GitBundle.message("checkout.operation.however.checkout.has.succeeded.for.the.following", repositories.size()))
      .br()
      .appendRaw(successfulRepositoriesJoined())
      .br()
      .append(rollBackProposal)
      .toString();
  }

  @NotNull
  @Nls
  @Override
  protected String getOperationName() {
    return GitBundle.message("checkout.operation.name");
  }

  @Override
  protected void rollback() {
    GitCompoundResult checkoutResult = new GitCompoundResult(myProject);
    GitCompoundResult deleteResult = new GitCompoundResult(myProject);
    for (GitRepository repository : getSuccessfulRepositories()) {
      Hash startHash = getHead(repository);
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
      updateAndRefreshChangedVfs(repository, startHash);
    }
    if (!checkoutResult.totalSuccess() || !deleteResult.totalSuccess()) {
      @NlsContexts.NotificationContent StringBuilder message = new StringBuilder();
      if (!checkoutResult.totalSuccess()) {
        message.append(GitBundle.message("checkout.operation.errors.during.checkout"));
        message.append(checkoutResult.getErrorOutputWithReposIndication());
      }
      if (!deleteResult.totalSuccess()) {
        message.append(GitBundle.message("checkout.operation.errors.during.deleting", code(myNewBranch)));
        message.append(deleteResult.getErrorOutputWithReposIndication());
      }
      VcsNotifier.getInstance(myProject).notifyError(CHECKOUT_ROLLBACK_ERROR,
                                                     GitBundle.message("checkout.operation.error.during.rollback"),
                                                     message.toString(),
                                                     true);
    }
  }

  @NotNull
  @NlsContexts.NotificationTitle
  private String getCommonErrorTitle() {
    return GitBundle.message("checkout.operation.could.not.checkout.error.title", getRefPresentation(myStartPointReference));
  }

  @NotNull
  @Override
  protected String getSuccessMessage() {
    if (myNewBranch == null) {
      return GitBundle.message("checkout.operation.checked.out",
                               bold(code(myStartPointReference)));
    }
    return GitBundle.message("checkout.operation.checked.out.new.branch.from",
                             bold(code(myNewBranch)),
                             bold(code(getRefPresentation(myStartPointReference))));
  }

  @NotNull
  private static String getRefPresentation(@NotNull String reference) {
    return StringUtil.substringBeforeLast(reference, "^0");
  }

  // stash - checkout - unstash
  private boolean smartCheckout(@NotNull final List<? extends GitRepository> repositories,
                                @NotNull @NlsSafe final String reference,
                                @Nullable final String newBranch,
                                @NotNull ProgressIndicator indicator,
                                @NotNull StructuredIdeActivity activity) {
    AtomicBoolean result = new AtomicBoolean();
    GitSaveChangesPolicy saveMethod = GitVcsSettings.getInstance(myProject).getSaveChangesPolicy();
    GitPreservingProcess preservingProcess =
      new GitPreservingProcess(myProject,
                               myGit,
                               getRootsFromRepositories(repositories),
                               GitBundle.message("checkout.operation.name"),
                               reference,
                               saveMethod,
                               indicator,
                               () -> result.set(checkoutOrNotify(repositories, reference, newBranch, false, activity)));
    preservingProcess.execute();
    return result.get();
  }

  /**
   * Checks out or shows an error message.
   */
  private boolean checkoutOrNotify(@NotNull List<? extends GitRepository> repositories,
                                   @NotNull String reference,
                                   @Nullable String newBranch,
                                   boolean force,
                                   @NotNull StructuredIdeActivity activity) {
    GitCompoundResult compoundResult = new GitCompoundResult(myProject);
    StructuredIdeActivity checkoutOperation = CHECKOUT_OPERATION.startedWithParent(myProject, activity);
    for (GitRepository repository : repositories) {
      compoundResult.append(repository, myGit.checkout(repository, reference, newBranch, force, myDetach, myReset));
    }
    checkoutOperation.finished();
    if (compoundResult.totalSuccess()) {
      return true;
    }
    notifyError(GitBundle.message("checkout.operation.could.not.checkout.error", reference),
                compoundResult.getErrorOutputWithReposIndication());
    return false;
  }

  private class RollbackOperationNotificationListener implements NotificationListener {
    @Override
    public void hyperlinkUpdate(@NotNull Notification notification,
                                @NotNull HyperlinkEvent event) {
      if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED && event.getDescription().equalsIgnoreCase(ROLLBACK_HREF_ATTRIBUTE)) {
        rollback();
      }
    }
  }
}
