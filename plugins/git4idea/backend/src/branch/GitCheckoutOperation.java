// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
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
import com.intellij.platform.diagnostic.telemetry.TelemetryManager;
import com.intellij.platform.diagnostic.telemetry.helpers.TraceKt;
import com.intellij.vcs.log.Hash;
import git4idea.GitActivity;
import git4idea.GitProtectedBranchesKt;
import git4idea.GitWorkingTree;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitBranchAlreadyCheckedOutInOtherWorktreeDetector;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector;
import git4idea.commands.GitMessageWithFilesDetector;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.commands.GitUntrackedFilesOverwrittenByOperationDetector;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.config.GitSharedSettings;
import git4idea.config.GitVcsSettings;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import git4idea.util.GitFreezingProcess;
import git4idea.util.GitPreservingProcess;
import git4idea.workingTrees.GitWorkingTreesService;
import io.opentelemetry.api.trace.Tracer;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.dvcs.DvcsUtil.joinShortNames;
import static com.intellij.platform.vcs.impl.shared.telemetry.VcsScopeKt.VcsScope;
import static com.intellij.util.containers.UtilKt.getIfSingle;
import static git4idea.GitBranchesUsageCollector.CHECKOUT_ACTIVITY;
import static git4idea.GitBranchesUsageCollector.CHECKOUT_OPERATION;
import static git4idea.GitBranchesUsageCollector.FINISHED_SUCCESSFULLY;
import static git4idea.GitBranchesUsageCollector.IS_BRANCH_PROTECTED;
import static git4idea.GitBranchesUsageCollector.IS_NEW_BRANCH;
import static git4idea.GitBranchesUsageCollector.VFS_REFRESH;
import static git4idea.GitNotificationIdsHolder.CHECKOUT_ROLLBACK_ERROR;
import static git4idea.GitNotificationIdsHolder.CHECKOUT_SUCCESS;
import static git4idea.GitUtil.getHead;
import static git4idea.GitUtil.getRootsFromRepositories;
import static git4idea.GitUtil.refreshVfs;
import static git4idea.GitUtil.toAbsolute;
import static git4idea.GitUtil.updateAndRefreshChangedVfs;
import static git4idea.GitUtil.updateRepositories;
import static git4idea.branch.GitSmartOperationDialog.Choice.FORCE;
import static git4idea.branch.GitSmartOperationDialog.Choice.SMART;
import static git4idea.telemetry.GitBackendTelemetrySpan.Operation;
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

  private final @NotNull String myStartPointReference;
  private final boolean myDetach;
  private final boolean myReset;
  private final boolean myRefShouldBeValid;
  private final @Nullable String myNewBranch;

  /**
   * Repositories for which the user chose to open the already existing worktree instead of checking out.
   * They are neither successful nor skipped, so they are tracked separately to avoid misreporting them.
   */
  private final @NotNull Collection<GitRepository> myOpenedInOtherWorktreeRepositories = new ArrayList<>();

  private enum OtherWorktreeConflictOutcome { SUCCESS, OPENED_EXISTING_WORKTREE, FATAL_ERROR }

  /**
   * Outcome of {@link #runCheckoutAndHandleCommonFailures}: {@code UNHANDLED} means none of the three detectors
   * common to every checkout attempt fired, leaving the caller to interpret its own attempt-specific detectors.
   */
  private enum CheckoutAttemptOutcome { SUCCESS, FATAL_ERROR, UNHANDLED }

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
    new GitFreezingProcess(myProject, getOperationName(), this::doExecuteWithTracing).execute();
  }

  private void doExecuteWithTracing() {
    Tracer tracer = TelemetryManager.getInstance().getTracer(VcsScope);
    TraceKt.use(tracer.spanBuilder(Operation.Checkout.getName()).setAttribute("branch", myNewBranch != null ? myNewBranch : "null"), _ -> {
      StructuredIdeActivity checkoutActivity = CHECKOUT_ACTIVITY.started(myProject, () -> List.of(
        IS_BRANCH_PROTECTED.with(isBranchProtected()),
        IS_NEW_BRANCH.with(myNewBranch != null)
      ));
      Ref<Boolean> finishedSuccessfullyRef = Ref.create(false);

      try {
        finishedSuccessfullyRef.set(doExecute(checkoutActivity));
      }
      finally {
        checkoutActivity.finished(() -> {
          return List.of(FINISHED_SUCCESSFULLY.with(finishedSuccessfullyRef.get()));
        });
      }
      return null;
    });
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
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, GitBundle.message("activity.name.checkout"), GitActivity.Checkout)) {
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
        GitBranchAlreadyCheckedOutInOtherWorktreeDetector otherWorktreeDetector =
          new GitBranchAlreadyCheckedOutInOtherWorktreeDetector();

        StructuredIdeActivity checkoutOperation = CHECKOUT_OPERATION.startedWithParent(myProject, activity);
        GitCommandResult result;
        try {
          result = myGit.checkout(repository, myStartPointReference, myNewBranch, false, myDetach, myReset, false,
                                  localChangesDetector, unmergedFiles, unknownPathspec, untrackedOverwrittenByCheckout,
                                  otherWorktreeDetector);
        }
        finally {
          checkoutOperation.finished();
        }

        CheckoutAttemptOutcome outcome = runCheckoutAndHandleCommonFailures(
          repository, result, localChangesDetector, unmergedFiles, untrackedOverwrittenByCheckout, activity, startHash);
        if (outcome == CheckoutAttemptOutcome.FATAL_ERROR) {
          fatalErrorHappened = true;
        }
        else if (outcome == CheckoutAttemptOutcome.UNHANDLED) {
          if (!myRefShouldBeValid && unknownPathspec.isDetected()) {
            markSkip(repository);
          }
          else if (otherWorktreeDetector.isDetected()) {
            OtherWorktreeConflictOutcome otherWorktreeOutcome =
              checkoutIgnoringOtherWorktreeOrNotify(repository, result, otherWorktreeDetector, activity, startHash);
            if (otherWorktreeOutcome == OtherWorktreeConflictOutcome.FATAL_ERROR) {
              fatalErrorHappened = true;
            }
            else if (otherWorktreeOutcome == OtherWorktreeConflictOutcome.OPENED_EXISTING_WORKTREE) {
              handleOpenedInOtherWorktree(repository);
            }
          }
          else {
            fatalError(getCommonErrorTitle(), result);
            fatalErrorHappened = true;
          }
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

          VcsNotifier.toolWindowNotification()
            .createNotification("", builder.toString(), NotificationType.INFORMATION)
            .setDisplayId(CHECKOUT_SUCCESS)
            .addAction(NotificationAction.createSimple(GitBundle.messagePointer("checkout.operation.rollback.action"), () -> {
              rollback();
            }))
            .notify(myProject);
        }
        success = true;
        notifyBranchHasChanged(myStartPointReference);
        updateRecentBranch();
      }
      else if (!myOpenedInOtherWorktreeRepositories.isEmpty()) {
        // the user chose to open an already existing worktree instead of checking out for some repositories;
        // other repositories may still have been skipped for an unrelated reason (invalid ref) and that must still be reported.
        if (wereSkipped()) {
          notifyError(GitBundle.message("checkout.operation.could.not.checkout.error", getRefPresentation(myStartPointReference)),
                      revisionNotFound);
        }
      }
      else {
        LOG.assertTrue(!myRefShouldBeValid);
        notifyError(GitBundle.message("checkout.operation.could.not.checkout.error", getRefPresentation(myStartPointReference)),
                    revisionNotFound);
      }
    }
    return success;
  }

  /**
   * Interprets a checkout attempt's result against the three detectors common to every checkout attempt (local
   * changes, unmerged files, untracked files that would be overwritten) and applies the matching recovery (VFS
   * refresh, smart checkout, fatal error dialogs). Returns {@link CheckoutAttemptOutcome#UNHANDLED} when none of
   * these common cases apply, leaving the caller to interpret its own attempt-specific detectors.
   */
  private @NotNull CheckoutAttemptOutcome runCheckoutAndHandleCommonFailures(
    @NotNull GitRepository repository,
    @NotNull GitCommandResult result,
    @NotNull GitLocalChangesWouldBeOverwrittenDetector localChangesDetector,
    @NotNull GitSimpleEventDetector unmergedFiles,
    @NotNull GitUntrackedFilesOverwrittenByOperationDetector untrackedOverwrittenByCheckout,
    @NotNull StructuredIdeActivity activity,
    @Nullable Hash startHash) {
    if (result.success()) {
      StructuredIdeActivity vfsRefresh = VFS_REFRESH.startedWithParent(myProject, activity);
      try {
        updateAndRefreshChangedVfs(repository, startHash);
      }
      finally {
        vfsRefresh.finished();
      }
      markSuccessful(repository);
      return CheckoutAttemptOutcome.SUCCESS;
    }
    if (unmergedFiles.isDetected()) {
      fatalUnmergedFilesError();
      return CheckoutAttemptOutcome.FATAL_ERROR;
    }
    if (localChangesDetector.isDetected()) {
      boolean smartCheckoutSucceeded = smartCheckoutOrNotify(repository, localChangesDetector, activity);
      return smartCheckoutSucceeded ? CheckoutAttemptOutcome.SUCCESS : CheckoutAttemptOutcome.FATAL_ERROR;
    }
    if (untrackedOverwrittenByCheckout.isDetected()) {
      fatalUntrackedFilesError(repository.getRoot(), untrackedOverwrittenByCheckout.getRelativeFilePaths());
      return CheckoutAttemptOutcome.FATAL_ERROR;
    }
    return CheckoutAttemptOutcome.UNHANDLED;
  }

  /**
   * Marks the repository as handled by opening its existing worktree instead of checking it out here, so it is
   * removed from further processing without being counted as either successful or skipped.
   */
  private void handleOpenedInOtherWorktree(@NotNull GitRepository repository) {
    myOpenedInOtherWorktreeRepositories.add(repository);
    markHandledExternally(repository);
  }

  /**
   * Handles the "branch is already checked out in another worktree" conflict: asks the user for confirmation and, if confirmed,
   * retries the checkout with {@code --ignore-other-worktrees}. The retry keeps watching for local changes / unmerged / untracked
   * files, same as the original checkout attempt, so a conflict discovered only on retry still goes through the usual recovery
   * (smart checkout, fatal error dialogs) instead of falling straight to a generic error.
   */
  private @NotNull OtherWorktreeConflictOutcome checkoutIgnoringOtherWorktreeOrNotify(@NotNull GitRepository repository,
                                                                                      @NotNull GitCommandResult failedResult,
                                                                                      @NotNull GitBranchAlreadyCheckedOutInOtherWorktreeDetector detector,
                                                                                      @NotNull StructuredIdeActivity activity,
                                                                                      @Nullable Hash startHash) {
    GitBranchUiHandler.CheckoutInOtherWorktreeDecision decision = showCheckoutInOtherWorktreeDialog(detector, myStartPointReference);
    if (decision == GitBranchUiHandler.CheckoutInOtherWorktreeDecision.OPEN_EXISTING_WORKTREE) {
      openExistingWorktree(detector.getMatch() != null ? detector.getMatch().getWorktreePath() : null);
      return OtherWorktreeConflictOutcome.OPENED_EXISTING_WORKTREE;
    }
    if (decision != GitBranchUiHandler.CheckoutInOtherWorktreeDecision.CHECKOUT_ANYWAY) {
      fatalError(getCommonErrorTitle(), failedResult);
      return OtherWorktreeConflictOutcome.FATAL_ERROR;
    }

    VirtualFile root = repository.getRoot();
    GitLocalChangesWouldBeOverwrittenDetector localChangesDetector =
      new GitLocalChangesWouldBeOverwrittenDetector(root, GitLocalChangesWouldBeOverwrittenDetector.Operation.CHECKOUT);
    GitSimpleEventDetector unmergedFiles = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED_PREVENTING_CHECKOUT);
    GitUntrackedFilesOverwrittenByOperationDetector untrackedOverwrittenByCheckout =
      new GitUntrackedFilesOverwrittenByOperationDetector(root);
    GitCommandResult retryResult = myGit.checkout(repository, myStartPointReference, myNewBranch, false, myDetach, myReset, true,
                                                  localChangesDetector, unmergedFiles, untrackedOverwrittenByCheckout);
    CheckoutAttemptOutcome outcome = runCheckoutAndHandleCommonFailures(
      repository, retryResult, localChangesDetector, unmergedFiles, untrackedOverwrittenByCheckout, activity, startHash);
    if (outcome == CheckoutAttemptOutcome.SUCCESS) {
      return OtherWorktreeConflictOutcome.SUCCESS;
    }
    if (outcome == CheckoutAttemptOutcome.FATAL_ERROR) {
      return OtherWorktreeConflictOutcome.FATAL_ERROR;
    }
    fatalError(getCommonErrorTitle(), retryResult);
    return OtherWorktreeConflictOutcome.FATAL_ERROR;
  }

  private @NotNull GitBranchUiHandler.CheckoutInOtherWorktreeDecision showCheckoutInOtherWorktreeDialog(
    @NotNull GitBranchAlreadyCheckedOutInOtherWorktreeDetector detector, @NotNull String reference) {
    GitBranchAlreadyCheckedOutInOtherWorktreeDetector.Match match = detector.getMatch();
    String branchName = match != null ? match.getBranchName() : getRefPresentation(reference);
    return myUiHandler.showCheckoutBranchInOtherWorktreeDialog(branchName, match != null ? match.getWorktreePath() : null);
  }

  private void openExistingWorktree(@Nullable String worktreePath) {
    if (worktreePath == null) return;
    GitWorkingTree tree = new GitWorkingTree(worktreePath, null, false, false, false, false, null);
    GitWorkingTreesService.Companion.getInstance(myProject).openWorkingTreeProject(tree, null);
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
      Collection<GitRepository> openedInOtherWorktree = new ArrayList<>();
      boolean smartCheckedOutSuccessfully
        = smartCheckout(allConflictingRepositories, myStartPointReference, myNewBranch, getIndicator(), activity, openedInOtherWorktree);
      if (smartCheckedOutSuccessfully) {
        for (GitRepository conflictingRepository : allConflictingRepositories) {
          if (openedInOtherWorktree.contains(conflictingRepository)) {
            handleOpenedInOtherWorktree(conflictingRepository);
            continue;
          }
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
      Collection<GitRepository> openedInOtherWorktree = new ArrayList<>();
      boolean forceCheckoutSucceeded =
        checkoutOrNotify(allConflictingRepositories, myStartPointReference, myNewBranch, true, activity, openedInOtherWorktree);
      if (forceCheckoutSucceeded) {
        openedInOtherWorktree.forEach(this::handleOpenedInOtherWorktree);
        List<GitRepository> checkedOutRepositories = new ArrayList<>(allConflictingRepositories);
        checkedOutRepositories.removeAll(openedInOtherWorktree);
        markSuccessful(checkedOutRepositories.toArray(new GitRepository[0]));
        updateRepositories(checkedOutRepositories);
        StructuredIdeActivity vfsRefresh = VFS_REFRESH.startedWithParent(myProject, activity);
        checkedOutRepositories.forEach(repo -> refreshVfs(repo.getRoot(), changesToRefresh.get(repo)));
        vfsRefresh.finished();
      }
      return forceCheckoutSucceeded;
    }
    else {
      fatalLocalChangesError(myStartPointReference);
      return false;
    }
  }

  @Override
  protected @NotNull String getRollbackProposal() {
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

  @Override
  protected @NotNull @Nls String getOperationName() {
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

  private @NotNull @NlsContexts.NotificationTitle String getCommonErrorTitle() {
    return GitBundle.message("checkout.operation.could.not.checkout.error.title", getRefPresentation(myStartPointReference));
  }

  @Override
  protected @NotNull String getSuccessMessage() {
    if (myNewBranch == null) {
      return GitBundle.message("checkout.operation.checked.out",
                               code(myStartPointReference));
    }
    return GitBundle.message("checkout.operation.checked.out.new.branch.from",
                             code(myNewBranch),
                             code(getRefPresentation(myStartPointReference)));
  }

  private static @NotNull String getRefPresentation(@NotNull String reference) {
    return StringUtil.substringBeforeLast(reference, "^0");
  }

  // stash - checkout - unstash
  private boolean smartCheckout(final @NotNull List<? extends GitRepository> repositories,
                                final @NotNull @NlsSafe String reference,
                                final @Nullable String newBranch,
                                @NotNull ProgressIndicator indicator,
                                @NotNull StructuredIdeActivity activity,
                                @NotNull Collection<GitRepository> openedInOtherWorktreeOut) {
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
                               () -> result.set(checkoutOrNotify(repositories, reference, newBranch, false, activity, openedInOtherWorktreeOut)));
    preservingProcess.execute();
    return result.get();
  }

  /**
   * Checks out or shows an error message.
   * Repositories for which the user chose to open the already existing worktree instead of checking out are added
   * to {@code openedInOtherWorktreeOut}; they count neither as successful nor as failed, so callers should exclude
   * them from post-success bookkeeping (marking successful, refreshing VFS, etc).
   */
  private boolean checkoutOrNotify(@NotNull List<? extends GitRepository> repositories,
                                   @NotNull String reference,
                                   @Nullable String newBranch,
                                   boolean force,
                                   @NotNull StructuredIdeActivity activity,
                                   @NotNull Collection<GitRepository> openedInOtherWorktreeOut) {
    GitCompoundResult compoundResult = new GitCompoundResult(myProject);
    StructuredIdeActivity checkoutOperation = CHECKOUT_OPERATION.startedWithParent(myProject, activity);
    for (GitRepository repository : repositories) {
      GitBranchAlreadyCheckedOutInOtherWorktreeDetector otherWorktreeDetector = new GitBranchAlreadyCheckedOutInOtherWorktreeDetector();
      GitCommandResult result = myGit.checkout(repository, reference, newBranch, force, myDetach, myReset, false, otherWorktreeDetector);
      if (!result.success() && otherWorktreeDetector.isDetected()) {
        GitBranchUiHandler.CheckoutInOtherWorktreeDecision decision = showCheckoutInOtherWorktreeDialog(otherWorktreeDetector, reference);
        if (decision == GitBranchUiHandler.CheckoutInOtherWorktreeDecision.CHECKOUT_ANYWAY) {
          result = myGit.checkout(repository, reference, newBranch, force, myDetach, myReset, true);
        }
        else if (decision == GitBranchUiHandler.CheckoutInOtherWorktreeDecision.OPEN_EXISTING_WORKTREE) {
          openExistingWorktree(otherWorktreeDetector.getMatch() != null ? otherWorktreeDetector.getMatch().getWorktreePath() : null);
          openedInOtherWorktreeOut.add(repository);
          continue;
        }
      }
      compoundResult.append(repository, result);
    }
    checkoutOperation.finished();
    if (compoundResult.totalSuccess()) {
      return true;
    }
    notifyError(GitBundle.message("checkout.operation.could.not.checkout.error", reference),
                compoundResult.getErrorOutputWithReposIndication());
    return false;
  }
}
