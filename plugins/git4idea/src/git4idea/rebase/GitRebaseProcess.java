// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.CommonBundle;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.progress.StepsProgressIndicator;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.TimedVcsCommit;
import git4idea.*;
import git4idea.branch.GitRebaseParams;
import git4idea.commands.*;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.stash.GitChangesSaver;
import git4idea.util.GitFreezingProcess;
import git4idea.util.GitUntrackedFilesHelper;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.openapi.ui.Messages.getWarningIcon;
import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static git4idea.GitActionIdsHolder.Id.*;
import static git4idea.GitNotificationIdsHolder.REBASE_NOT_STARTED;
import static git4idea.GitNotificationIdsHolder.REBASE_SUCCESSFUL;
import static git4idea.GitUtil.*;
import static git4idea.rebase.conflict.GitRebaseMergeDialogCustomizerKt.createRebaseDialogCustomizer;
import static java.util.Collections.singleton;

public class GitRebaseProcess {

  private static final Logger LOG = Logger.getInstance(GitRebaseProcess.class);

  private final NotificationAction ABORT_ACTION = NotificationAction.createSimpleExpiring(
    GitBundle.message("rebase.notification.action.abort.text"),
    ABORT.id, this::abort
  );
  private final NotificationAction CONTINUE_ACTION = NotificationAction.createSimpleExpiring(
    GitBundle.message("rebase.notification.action.continue.text"),
    CONTINUE.id, () -> retry(GitBundle.message("rebase.progress.indicator.continue.title"))
  );
  private final NotificationAction RETRY_ACTION = NotificationAction.createSimpleExpiring(
    GitBundle.message("rebase.notification.action.retry.text"),
    RETRY.id, () -> retry(GitBundle.message("rebase.progress.indicator.retry.title"))
  );
  private final NotificationAction VIEW_STASH_ACTION;

  private final @NotNull Project myProject;
  private final @NotNull Git myGit;
  private final @NotNull ChangeListManager myChangeListManager;
  private final @NotNull VcsNotifier myNotifier;
  private final @NotNull GitRepositoryManager myRepositoryManager;

  private final @NotNull GitRebaseSpec myRebaseSpec;
  private final @Nullable GitRebaseResumeMode myCustomMode;
  private final @NotNull GitChangesSaver mySaver;
  private final @NotNull ProgressManager myProgressManager;
  private final @NotNull VcsDirtyScopeManager myDirtyScopeManager;

  public GitRebaseProcess(@NotNull Project project, @NotNull GitRebaseSpec rebaseSpec, @Nullable GitRebaseResumeMode customMode) {
    myProject = project;
    myRebaseSpec = rebaseSpec;
    myCustomMode = customMode;
    mySaver = rebaseSpec.getSaver();

    myGit = Git.getInstance();
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myNotifier = VcsNotifier.getInstance(myProject);
    myRepositoryManager = getRepositoryManager(myProject);
    myProgressManager = ProgressManager.getInstance();
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);

    VIEW_STASH_ACTION = new GitRestoreSavedChangesNotificationAction(mySaver);
  }

  public void rebase() {
    if (checkForRebasingPublishedCommits()) {
      new GitFreezingProcess(myProject, GitBundle.message("rebase.git.operation.name"), this::doRebase).execute();
    }
  }

  /**
   * Given a GitRebaseSpec this method either starts, or continues the ongoing rebase in multiple repositories.
   * <ul>
   * <li>It does nothing with "already successfully rebased repositories" (the ones which have {@link GitRebaseStatus} == SUCCESSFUL,
   * and just remembers them to use in the resulting notification.</li>
   * <li>If there is a repository with rebase in progress, it calls `git rebase --continue` (or `--skip`).
   * It is assumed that there is only one such repository.</li>
   * <li>For all remaining repositories rebase on which didn't start yet, it calls {@code git rebase <original parameters>}</li>
   * </ul>
   */
  private void doRebase() {
    LOG.info("Started rebase");
    LOG.debug("Started rebase with the following spec: " + myRebaseSpec);

    Map<GitRepository, GitRebaseStatus> statuses = new LinkedHashMap<>(myRebaseSpec.getStatuses());
    List<GitRepository> repositoriesToRebase = myRepositoryManager.sortByDependency(myRebaseSpec.getIncompleteRepositories());
    if (repositoriesToRebase.isEmpty()) {
      LOG.info("Nothing to rebase");
      return;
    }

    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, GitBundle.message("activity.name.rebase"), GitActivity.Rebase)) {
      if (!saveDirtyRootsInitially(repositoriesToRebase)) return;

      GitRepository latestRepository = null;
      ProgressIndicator showingIndicator = myProgressManager.getProgressIndicator();
      StepsProgressIndicator indicator = new StepsProgressIndicator(
        showingIndicator != null ? showingIndicator : new EmptyProgressIndicator(),
        repositoriesToRebase.size()
      );
      indicator.setIndeterminate(false);
      for (GitRepository repository : repositoriesToRebase) {
        GitRebaseResumeMode customMode = null;
        if (repository == myRebaseSpec.getOngoingRebase()) {
          customMode = myCustomMode == null ? GitRebaseResumeMode.CONTINUE : myCustomMode;
        }

        Hash startHash = getHead(repository);

        GitRebaseStatus rebaseStatus = rebaseSingleRoot(repository, customMode, getSuccessfulRepositories(statuses), indicator);
        indicator.nextStep();
        repository.update(); // make the repo state info actual ASAP
        if (customMode == GitRebaseResumeMode.CONTINUE) {
          myDirtyScopeManager.dirDirtyRecursively(repository.getRoot());
        }

        latestRepository = repository;
        statuses.put(repository, rebaseStatus);
        refreshChangedVfs(repository, startHash);
        if (rebaseStatus.getType() != GitRebaseStatus.Type.SUCCESS) {
          break;
        }
      }

      GitRebaseStatus.Type latestStatus = statuses.get(latestRepository).getType();
      if (latestStatus == GitRebaseStatus.Type.SUCCESS || latestStatus == GitRebaseStatus.Type.NOT_STARTED) {
        LOG.debug("Rebase completed successfully.");
        mySaver.load();
      }
      if (latestStatus == GitRebaseStatus.Type.SUCCESS) {
        notifySuccess();
      }

      saveUpdatedSpec(statuses);
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch(Throwable e) {
      myRepositoryManager.setOngoingRebaseSpec(null);
      ExceptionUtil.rethrowUnchecked(e);
    }
  }

  private void saveUpdatedSpec(@NotNull Map<GitRepository, GitRebaseStatus> statuses) {
    if (myRebaseSpec.shouldBeSaved()) {
      GitRebaseSpec newRebaseInfo = myRebaseSpec.cloneWithNewStatuses(statuses);
      myRepositoryManager.setOngoingRebaseSpec(newRebaseInfo);
    }
    else {
      myRepositoryManager.setOngoingRebaseSpec(null);
    }
  }

  private @NotNull GitRebaseStatus rebaseSingleRoot(@NotNull GitRepository repository,
                                                    @Nullable GitRebaseResumeMode customMode,
                                                    @NotNull Map<GitRepository, GitSuccessfulRebase> alreadyRebased,
                                                    @NotNull ProgressIndicator indicator) {
    VirtualFile root = repository.getRoot();
    String repoName = getShortRepositoryName(repository);
    LOG.info("Rebasing root " + repoName + ", mode: " + notNull(customMode, "standard"));

    boolean retryWhenDirty = false;

    int commitsToRebase = 0;
    try {
      GitRebaseParams params = myRebaseSpec.getParams();
      if (params != null) {
        String upstream = params.getUpstream();
        String branch = params.getBranch();
        commitsToRebase = GitRebaseUtils.getNumberOfCommitsToRebase(repository, upstream, branch);
      }
    }
    catch (VcsException e) {
      LOG.warn("Couldn't get the number of commits to rebase", e);
    }
    GitRebaseProgressListener progressListener = new GitRebaseProgressListener(commitsToRebase, indicator);

    while (true) {
      GitRebaseProblemDetector rebaseDetector = new GitRebaseProblemDetector();
      GitUntrackedFilesOverwrittenByOperationDetector untrackedDetector = new GitUntrackedFilesOverwrittenByOperationDetector(root);
      GitRebaseCommandResult rebaseCommandResult = callRebase(repository, customMode, rebaseDetector, untrackedDetector, progressListener);
      GitCommandResult result = rebaseCommandResult.getCommandResult();

      boolean somethingRebased = customMode != null || progressListener.currentCommit > 1;

      if (rebaseCommandResult.wasCancelledInCommitList()) {
        return GitRebaseStatus.notStarted();
      }
      else if (rebaseCommandResult.wasCancelledInCommitMessage()) {
        showStoppedForEditingMessage();
        return new GitRebaseStatus(GitRebaseStatus.Type.SUSPENDED);
      }
      else if (result.success()) {
        if (rebaseDetector.hasStoppedForEditing()) {
          showStoppedForEditingMessage();
          return new GitRebaseStatus(GitRebaseStatus.Type.SUSPENDED);
        }
        LOG.debug("Successfully rebased " + repoName);
        return new GitSuccessfulRebase();
      }
      else if (rebaseDetector.isDirtyTree() && customMode == null && !retryWhenDirty) {
        // if the initial dirty tree check doesn't find all local changes, we are still ready to stash-on-demand,
        // but only once per repository (if the error happens again, that means that the previous stash attempt failed for some reason),
        // and not in the case of --continue (where all local changes are expected to be committed) or --skip.
        LOG.debug("Dirty tree detected in " + repoName);
        String saveError = mySaver.saveLocalChangesOrError(singleton(repository.getRoot()));
        if (saveError == null) {
          retryWhenDirty = true; // try same repository again
        }
        else {
          LOG.warn(String.format(
            "Couldn't %s root %s: %s",
            mySaver.getSaveMethod() == GitSaveChangesPolicy.SHELVE ? "shelve" : "stash",
            repository.getRoot(),
            saveError
          ));
          showFatalError(saveError, repository, somethingRebased, alreadyRebased.keySet());
          GitRebaseStatus.Type type = somethingRebased ? GitRebaseStatus.Type.SUSPENDED : GitRebaseStatus.Type.ERROR;
          return new GitRebaseStatus(type);
        }
      }
      else if (untrackedDetector.isDetected()) {
        LOG.info("Untracked files detected in " + repoName);
        showUntrackedFilesError(untrackedDetector.getRelativeFilePaths(), repository, somethingRebased, alreadyRebased.keySet());
        GitRebaseStatus.Type type = somethingRebased ? GitRebaseStatus.Type.SUSPENDED : GitRebaseStatus.Type.ERROR;
        return new GitRebaseStatus(type);
      }
      else if (rebaseDetector.isNoChangeError()) {
        LOG.info("'No changes' situation detected in " + repoName);
        customMode = GitRebaseResumeMode.SKIP;
      }
      else if (rebaseDetector.isMergeConflict()) {
        LOG.info("Merge conflict in " + repoName);
        ResolveConflictResult resolveResult = showConflictResolver(repository, false);
        if (resolveResult == ResolveConflictResult.ALL_RESOLVED) {
          customMode = GitRebaseResumeMode.CONTINUE;
        }
        else if (resolveResult == ResolveConflictResult.NOTHING_TO_MERGE) {
          // the output is the same for the cases:
          // (1) "unresolved conflicts"
          // (2) "manual editing of a file not followed by `git add`
          // => we check if there are any unresolved conflicts, and if not, then it is the case #2 which we are not handling
          LOG.info("Unmerged changes while rebasing root " + repoName + ": " + result.getErrorOutputAsJoinedString());
          showFatalError(result.getErrorOutputAsHtmlString(), repository, somethingRebased, alreadyRebased.keySet());
          GitRebaseStatus.Type type = somethingRebased ? GitRebaseStatus.Type.SUSPENDED : GitRebaseStatus.Type.ERROR;
          return new GitRebaseStatus(type);
        }
        else {
          notifyNotAllConflictsResolved(repository);
          return new GitRebaseStatus(GitRebaseStatus.Type.SUSPENDED);
        }
      }
      else {
        String error = getErrorMessage(rebaseCommandResult, result, repoName);
        showFatalError(error, repository, somethingRebased, alreadyRebased.keySet());
        GitRebaseStatus.Type type = somethingRebased ? GitRebaseStatus.Type.SUSPENDED : GitRebaseStatus.Type.ERROR;
        return new GitRebaseStatus(type);
      }
    }
  }

  private static @NotNull @Nls String getErrorMessage(GitRebaseCommandResult rebaseCommandResult,
                                                      GitCommandResult result,
                                                      String repoName) {
    String error;
    if (rebaseCommandResult.getFailureCause() instanceof VcsException editingFailureCause) {
      error = editingFailureCause.getMessage();
    }
    else {
      error = result.getErrorOutputAsHtmlString();
      LOG.info("Error rebasing root " + repoName + ": " + result.getErrorOutputAsJoinedString());
    }
    return error;
  }

  private @NotNull GitRebaseCommandResult callRebase(@NotNull GitRepository repository,
                                                     @Nullable GitRebaseResumeMode mode,
                                                     GitLineHandlerListener @NotNull ... listeners) {
    if (mode == null) {
      GitRebaseParams params = Objects.requireNonNull(myRebaseSpec.getParams());
      return myGit.rebase(repository, params, listeners);
    }
    else if (mode == GitRebaseResumeMode.SKIP) {
      return myGit.rebaseSkip(repository, listeners);
    }
    else {
      LOG.assertTrue(mode == GitRebaseResumeMode.CONTINUE, "Unexpected rebase mode: " + mode);
      return myGit.rebaseContinue(repository, listeners);
    }
  }

  @VisibleForTesting
  protected @NotNull Collection<GitRepository> getDirtyRoots(@NotNull Collection<GitRepository> repositories) {
    return findRootsWithLocalChanges(repositories);
  }

  private boolean saveDirtyRootsInitially(@NotNull List<? extends GitRepository> repositories) {
    Collection<GitRepository> repositoriesToSave = filter(repositories, repository -> {
      return !repository.equals(myRebaseSpec.getOngoingRebase()); // no need to save anything when --continue/--skip is to be called
    });
    if (repositoriesToSave.isEmpty()) return true;
    Collection<VirtualFile> rootsToSave = getRootsFromRepositories(getDirtyRoots(repositoriesToSave));
    String error = mySaver.saveLocalChangesOrError(rootsToSave);
    if (error != null) {
      myNotifier.notifyError(REBASE_NOT_STARTED, GitBundle.message("rebase.notification.not.started.title"), error);
      return false;
    }
    return true;
  }

  private Collection<GitRepository> findRootsWithLocalChanges(@NotNull Collection<GitRepository> repositories) {
    return filter(repositories, repository -> myChangeListManager.haveChangesUnder(repository.getRoot()) != ThreeState.NO);
  }

  @RequiresBackgroundThread
  protected void notifySuccess() {
    String rebasedBranch = getCommonCurrentBranchNameIfAllTheSame(myRebaseSpec.getAllRepositories());
    GitRebaseParams params = myRebaseSpec.getParams();
    String baseBranch = params == null ? null
                                       : params.getUpstream() != null ? notNull(params.getNewBase(), params.getUpstream())
                                                                      : params.getNewBase();
    if (HEAD.equals(baseBranch)) {
      baseBranch = getItemIfAllTheSame(myRebaseSpec.getInitialBranchNames().values(), baseBranch);
    }
    String message = GitSuccessfulRebase.formatMessage(rebasedBranch, baseBranch, params != null && params.getBranch() != null);
    myNotifier.notifyMinorInfo(REBASE_SUCCESSFUL, GitBundle.message("rebase.notification.successful.title"), message);
  }

  private static @Nullable String getCommonCurrentBranchNameIfAllTheSame(@NotNull Collection<? extends GitRepository> repositories) {
    return getItemIfAllTheSame(map(repositories, Repository::getCurrentBranchName), null);
  }

  @Contract("_, !null -> !null")
  private static <T> T getItemIfAllTheSame(@NotNull Collection<? extends T> collection, @Nullable T defaultItem) {
    return new HashSet<>(collection).size() == 1 ? getFirstItem(collection) : defaultItem;
  }

  private void notifyNotAllConflictsResolved(@NotNull GitRepository conflictingRepository) {
    String description = GitRebaseUtils.mentionLocalChangesRemainingInStash(mySaver);
    Notification notification = VcsNotifier.importantNotification()
      .createNotification(GitBundle.message("rebase.notification.conflict.title"), description, NotificationType.WARNING)
      .setDisplayId(GitNotificationIdsHolder.REBASE_STOPPED_ON_CONFLICTS)
      .addAction(createResolveNotificationAction(conflictingRepository))
      .addAction(CONTINUE_ACTION)
      .addAction(ABORT_ACTION);
    if (mySaver.wereChangesSaved()) notification.addAction(VIEW_STASH_ACTION);
    myNotifier.notify(notification);
  }

  private @NotNull ResolveConflictResult showConflictResolver(@NotNull GitRepository conflicting, boolean calledFromNotification) {
    GitConflictResolver.Params params = new GitConflictResolver
      .Params(myProject)
      .setMergeDialogCustomizer(createRebaseDialogCustomizer(conflicting, myRebaseSpec))
      .setReverse(true);
    RebaseConflictResolver conflictResolver = new RebaseConflictResolver(myProject, conflicting, params, calledFromNotification);
    boolean allResolved = conflictResolver.merge();
    if (conflictResolver.myWasNothingToMerge) return ResolveConflictResult.NOTHING_TO_MERGE;
    if (allResolved) return ResolveConflictResult.ALL_RESOLVED;
    return ResolveConflictResult.UNRESOLVED_REMAIN;
  }

  private void showStoppedForEditingMessage() {
    Notification notification = VcsNotifier.importantNotification()
      .createNotification(GitBundle.message("rebase.notification.editing.title"), "", NotificationType.INFORMATION)
      .setDisplayId(GitNotificationIdsHolder.REBASE_STOPPED_ON_EDITING)
      .addAction(CONTINUE_ACTION)
      .addAction(ABORT_ACTION);
    myNotifier.notify(notification);
  }

  private void showFatalError(final @NotNull @Nls String error,
                              final @NotNull GitRepository currentRepository,
                              boolean somethingWasRebased,
                              final @NotNull Collection<GitRepository> successful) {
    HtmlBuilder descriptionBuilder = new HtmlBuilder();
    if (myRepositoryManager.moreThanOneRoot()) {
      descriptionBuilder.append(getShortRepositoryName(currentRepository) + ": ");
    }
    descriptionBuilder.appendRaw(error).br();
    descriptionBuilder.appendRaw(GitRebaseUtils.mentionLocalChangesRemainingInStash(mySaver));
    String title = myRebaseSpec.getOngoingRebase() == null
                   ? GitBundle.message("rebase.notification.failed.rebase.title")
                   : GitBundle.message("rebase.notification.failed.continue.title");
    Notification notification = VcsNotifier.importantNotification()
      .createNotification(title, descriptionBuilder.toString(), NotificationType.ERROR)
      .setDisplayId(GitNotificationIdsHolder.REBASE_FAILED)
      .addAction(RETRY_ACTION);
    if (somethingWasRebased || !successful.isEmpty()) {
      notification.addAction(ABORT_ACTION);
    }
    if (mySaver.wereChangesSaved()) {
      notification.addAction(VIEW_STASH_ACTION);
    }
    myNotifier.notify(notification);
  }

  private void showUntrackedFilesError(@NotNull Set<String> untrackedPaths,
                                       @NotNull GitRepository currentRepository,
                                       boolean somethingWasRebased,
                                       @NotNull Collection<GitRepository> successful) {
    String message = GitRebaseUtils.mentionLocalChangesRemainingInStash(mySaver);
    List<NotificationAction> actions = new ArrayList<>();
    actions.add(RETRY_ACTION);
    if (somethingWasRebased || !successful.isEmpty()) {
      actions.add(ABORT_ACTION);
    }
    if (mySaver.wereChangesSaved()) {
      actions.add(VIEW_STASH_ACTION);
    }
    GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(
      myProject,
      currentRepository.getRoot(),
      untrackedPaths,
      GitBundle.message("rebase.git.operation.name"),
      message,
      actions.toArray(new NotificationAction[0])
    );
  }

  private static @NotNull Map<GitRepository, GitSuccessfulRebase> getSuccessfulRepositories(@NotNull Map<GitRepository, GitRebaseStatus> statuses) {
    Map<GitRepository, GitSuccessfulRebase> map = new LinkedHashMap<>();
    for (GitRepository repository : statuses.keySet()) {
      GitRebaseStatus status = statuses.get(repository);
      if (status instanceof GitSuccessfulRebase) map.put(repository, (GitSuccessfulRebase)status);
    }
    return map;
  }

  private boolean checkForRebasingPublishedCommits() {
    if (myCustomMode != null || myRebaseSpec.getOngoingRebase() != null) {
      return true;
    }
    if (myRebaseSpec.getParams() == null) {
      LOG.error("Shouldn't happen. Spec: " + myRebaseSpec);
      return true;
    }

    String upstream = myRebaseSpec.getParams().getUpstream();
    for (GitRepository repository : myRebaseSpec.getAllRepositories()) {
      String currentBranchName = chooseNotNull(repository.getCurrentBranchName(), repository.getCurrentRevision());
      if (currentBranchName == null) {
        LOG.error("No current branch or revision in " + repository);
        return true;
      }
      String rebasingBranch = notNull(myRebaseSpec.getParams().getBranch(), currentBranchName);
      if (isRebasingPublishedCommit(repository, upstream, rebasingBranch)) {
        return askIfShouldRebasePublishedCommit();
      }
    }
    return true;
  }

  public static boolean isRebasingPublishedCommit(@NotNull GitRepository repository,
                                                  @Nullable String baseBranch,
                                                  @NotNull String rebasingBranch) {
    try {
      String range = GitRebaseUtils.getCommitsRangeToRebase(baseBranch, rebasingBranch);
      List<? extends TimedVcsCommit> commits = GitHistoryUtils.collectTimedCommits(repository.getProject(), repository.getRoot(), range);
      return exists(commits, commit -> GitProtectedBranchesKt.isCommitPublished(repository, commit.getId()));
    }
    catch (VcsException e) {
      LOG.error("Couldn't collect commits", e);
      return true;
    }
  }

  public static boolean askIfShouldRebasePublishedCommit() {
    Ref<Boolean> rebaseAnyway = Ref.create(false);
    String message = new HtmlBuilder()
      .append(GitBundle.message("rebase.confirmation.dialog.published.commits.message.first")).br()
      .append(GitBundle.message("rebase.confirmation.dialog.published.commits.message.second"))
      .wrapWith(HtmlChunk.html())
      .toString();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      int answer = DialogManager.showMessage(
        message,
        GitBundle.message("rebase.confirmation.dialog.published.commits.title"),
        new String[]{
          GitBundle.message("rebase.confirmation.dialog.published.commits.button.rebase.text"),
          GitBundle.message("rebase.confirmation.dialog.published.commits.button.cancel.text")
        },
        1,
        1,
        getWarningIcon(),
        null
      );
      rebaseAnyway.set(answer == 0);
    });
    return rebaseAnyway.get();
  }

  private class RebaseConflictResolver extends GitConflictResolver {
    private final boolean myCalledFromNotification;
    private boolean myWasNothingToMerge;

    RebaseConflictResolver(@NotNull Project project,
                           @NotNull GitRepository repository,
                           @NotNull Params params, boolean calledFromNotification) {
      super(project, singleton(repository.getRoot()), params);
      myCalledFromNotification = calledFromNotification;
    }

    @Override
    protected void notifyUnresolvedRemain() {
      // will be handled in the common notification
    }

    @RequiresBackgroundThread
    @Override
    protected boolean proceedAfterAllMerged() {
      if (myCalledFromNotification) {
        retry(GitBundle.message("rebase.progress.indicator.continue.title"));
      }
      return true;
    }

    @Override
    protected boolean proceedIfNothingToMerge() {
      myWasNothingToMerge = true;
      return true;
    }
  }

  private enum ResolveConflictResult {
    ALL_RESOLVED,
    NOTHING_TO_MERGE,
    UNRESOLVED_REMAIN
  }

  private @NotNull NotificationAction createResolveNotificationAction(@NotNull GitRepository currentRepository) {
    return NotificationAction.create(GitBundle.message("action.NotificationAction.text.resolve"),
                                     RESOLVE.id, (e, notification) -> {
        myProgressManager.run(
          new Task.Backgroundable(myProject, GitBundle.message("rebase.progress.indicator.conflicts.collecting.title")) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              resolveConflicts(currentRepository, notification);
            }
          });
      });
  }

  private void resolveConflicts(@NotNull GitRepository currentRepository, @NotNull Notification notification) {
    ResolveConflictResult result = showConflictResolver(currentRepository, true);
    if (result == ResolveConflictResult.NOTHING_TO_MERGE) {
      ApplicationManager.getApplication().invokeLater(() -> {
        boolean continueRebase = MessageDialogBuilder.yesNo(GitBundle.message("rebase.notification.all.conflicts.resolved.title"),
                                                            GitBundle.message("rebase.notification.all.conflicts.resolved.text"))
          .yesText(GitBundle.message("rebase.notification.all.conflicts.resolved.continue.rebase.action.text"))
          .noText(CommonBundle.getCancelButtonText())
          .ask(myProject);
        if (continueRebase) {
          retry(GitBundle.message("rebase.progress.indicator.continue.title"));
          notification.expire();
        }
      });
    }
  }

  private void abort() {
    myProgressManager.run(new Task.Backgroundable(myProject, GitBundle.message("rebase.progress.indicator.aborting.title")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        GitRebaseUtils.abort(myProject, indicator);
      }
    });
  }

  private void retry(@NotNull @Nls String processTitle) {
    myProgressManager.run(new Task.Backgroundable(myProject, processTitle, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        GitRebaseUtils.continueRebase(myProject);
      }
    });
  }

  private static class GitRebaseProgressListener implements GitLineHandlerListener {
    private static final @NonNls Pattern REBASING_PATTERN = Pattern.compile("^Rebasing \\((\\d+)/(\\d+)\\)$");
    private static final @NonNls String APPLYING_PREFIX = "Applying: ";
    private int currentCommit = 0;

    private final int myCommitsToRebase;
    private final @NotNull ProgressIndicator myIndicator;

    GitRebaseProgressListener(int commitsToRebase, @NotNull ProgressIndicator indicator) {
      myCommitsToRebase = commitsToRebase;
      myIndicator = indicator;
    }

    @Override
    public void onLineAvailable(@NotNull String line, @NotNull Key outputType) {
      Matcher matcher = REBASING_PATTERN.matcher(line);
      if (matcher.matches()) {
        currentCommit = Integer.parseInt(matcher.group(1));
      }
      else if (StringUtil.startsWith(line, APPLYING_PREFIX)) {
        currentCommit++;
      }
      if (myCommitsToRebase != 0) {
        myIndicator.setFraction((double)currentCommit / myCommitsToRebase);
      }
    }
  }
}
