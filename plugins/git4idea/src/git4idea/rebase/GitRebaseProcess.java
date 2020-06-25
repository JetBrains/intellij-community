// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.diff.DiffEditorTitleCustomizer;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.progress.StepsProgressIndicator;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.util.VcsLogUtil;
import git4idea.DialogManager;
import git4idea.GitProtectedBranchesKt;
import git4idea.GitRevisionNumber;
import git4idea.branch.GitRebaseParams;
import git4idea.commands.*;
import git4idea.config.GitSaveChangesPolicy;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.merge.GitDefaultMergeDialogCustomizer;
import git4idea.merge.GitDefaultMergeDialogCustomizerKt;
import git4idea.merge.GitMergeProvider;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.stash.GitChangesSaver;
import git4idea.util.GitFreezingProcess;
import git4idea.util.GitUntrackedFilesHelper;
import kotlin.Pair;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.openapi.ui.Messages.getWarningIcon;
import static com.intellij.openapi.vcs.VcsNotifier.IMPORTANT_ERROR_NOTIFICATION;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.ObjectUtils.tryCast;
import static com.intellij.util.containers.ContainerUtil.*;
import static git4idea.GitUtil.*;
import static git4idea.merge.GitDefaultMergeDialogCustomizerKt.getTitleWithCommitDetailsCustomizer;
import static git4idea.merge.GitDefaultMergeDialogCustomizerKt.getTitleWithCommitsRangeDetailsCustomizer;
import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

public class GitRebaseProcess {

  private static final Logger LOG = Logger.getInstance(GitRebaseProcess.class);

  private final NotificationAction ABORT_ACTION = NotificationAction.createSimpleExpiring(
    GitBundle.message("rebase.notification.action.abort.text"),
    () -> abort()
  );
  private final NotificationAction CONTINUE_ACTION = NotificationAction.createSimpleExpiring(
    GitBundle.message("rebase.notification.action.continue.text"),
    () -> retry(GitBundle.getString("rebase.progress.indicator.continue.title"))
  );
  private final NotificationAction RETRY_ACTION = NotificationAction.createSimpleExpiring(
    GitBundle.message("rebase.notification.action.retry.text"),
    () -> retry(GitBundle.getString("rebase.progress.indicator.retry.title"))
  );
  private final NotificationAction VIEW_STASH_ACTION;

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final VcsNotifier myNotifier;
  @NotNull private final GitRepositoryManager myRepositoryManager;

  @NotNull private final GitRebaseSpec myRebaseSpec;
  @Nullable private final GitRebaseResumeMode myCustomMode;
  @NotNull private final GitChangesSaver mySaver;
  @NotNull private final ProgressManager myProgressManager;
  @NotNull private final VcsDirtyScopeManager myDirtyScopeManager;

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

    VIEW_STASH_ACTION = NotificationAction.createSimple(
      mySaver.getSaveMethod().selectBundleMessage(
        GitBundle.getString("rebase.notification.action.view.stash.text"),
        GitBundle.getString("rebase.notification.action.view.shelf.text")
      ),
      () -> mySaver.showSavedChanges()
    );
  }

  public void rebase() {
    if (checkForRebasingPublishedCommits()) {
      new GitFreezingProcess(myProject, GitBundle.getString("rebase.git.operation.name"), this::doRebase).execute();
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
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, "Rebase")) {
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
        if (shouldBeRefreshed(rebaseStatus)) {
          refreshChangedVfs(repository, startHash);
        }
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

  @NotNull
  private GitRebaseStatus rebaseSingleRoot(@NotNull GitRepository repository,
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
        String saveError = saveLocalChanges(singleton(repository.getRoot()));
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
      else if (untrackedDetector.wasMessageDetected()) {
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
        LOG.info("Error rebasing root " + repoName + ": " + result.getErrorOutputAsJoinedString());
        showFatalError(result.getErrorOutputAsHtmlString(), repository, somethingRebased, alreadyRebased.keySet());
        GitRebaseStatus.Type type = somethingRebased ? GitRebaseStatus.Type.SUSPENDED : GitRebaseStatus.Type.ERROR;
        return new GitRebaseStatus(type);
      }
    }
  }

  @NotNull
  private GitRebaseCommandResult callRebase(@NotNull GitRepository repository,
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
  @NotNull
  protected Collection<GitRepository> getDirtyRoots(@NotNull Collection<GitRepository> repositories) {
    return findRootsWithLocalChanges(repositories);
  }

  private static boolean shouldBeRefreshed(@NotNull GitRebaseStatus rebaseStatus) {
    return rebaseStatus.getType() != GitRebaseStatus.Type.SUCCESS;
  }

  private boolean saveDirtyRootsInitially(@NotNull List<? extends GitRepository> repositories) {
    Collection<GitRepository> repositoriesToSave = filter(repositories, repository -> {
      return !repository.equals(myRebaseSpec.getOngoingRebase()); // no need to save anything when --continue/--skip is to be called
    });
    if (repositoriesToSave.isEmpty()) return true;
    Collection<VirtualFile> rootsToSave = getRootsFromRepositories(getDirtyRoots(repositoriesToSave));
    String error = saveLocalChanges(rootsToSave);
    if (error != null) {
      myNotifier.notifyError(GitBundle.getString("rebase.notification.not.started.title"), error);
      return false;
    }
    return true;
  }

  @Nullable
  private String saveLocalChanges(@NotNull Collection<? extends VirtualFile> rootsToSave) {
    try {
      mySaver.saveLocalChanges(rootsToSave);
      return null;
    }
    catch (VcsException e) {
      LOG.warn(e);
      return mySaver.getSaveMethod().selectBundleMessage(
        GitBundle.message("rebase.notification.failed.stash.text", e.getMessage()),
        GitBundle.message("rebase.notification.failed.shelf.text", e.getMessage())
      );
    }
  }

  private Collection<GitRepository> findRootsWithLocalChanges(@NotNull Collection<GitRepository> repositories) {
    return filter(repositories, repository -> myChangeListManager.haveChangesUnder(repository.getRoot()) != ThreeState.NO);
  }

  @CalledInBackground
  protected void notifySuccess() {
    String rebasedBranch = getCommonCurrentBranchNameIfAllTheSame(myRebaseSpec.getAllRepositories());
    GitRebaseParams params = myRebaseSpec.getParams();
    String baseBranch = params == null ? null : notNull(params.getNewBase(), params.getUpstream());
    if (HEAD.equals(baseBranch)) {
      baseBranch = getItemIfAllTheSame(myRebaseSpec.getInitialBranchNames().values(), baseBranch);
    }
    String message = GitSuccessfulRebase.formatMessage(rebasedBranch, baseBranch, params != null && params.getBranch() != null);
    myNotifier.notifyMinorInfo(GitBundle.getString("rebase.notification.successful.title"), message);
  }

  @Nullable
  private static String getCommonCurrentBranchNameIfAllTheSame(@NotNull Collection<? extends GitRepository> repositories) {
    return getItemIfAllTheSame(map(repositories, Repository::getCurrentBranchName), null);
  }

  @Contract("_, !null -> !null")
  private static <T> T getItemIfAllTheSame(@NotNull Collection<? extends T> collection, @Nullable T defaultItem) {
    return new HashSet<>(collection).size() == 1 ? getFirstItem(collection) : defaultItem;
  }

  private void notifyNotAllConflictsResolved(@NotNull GitRepository conflictingRepository) {
    String description = GitRebaseUtils.mentionLocalChangesRemainingInStash(mySaver);
    Notification notification = IMPORTANT_ERROR_NOTIFICATION.createNotification(
      GitBundle.getString("rebase.notification.conflict.title"),
      description,
      NotificationType.WARNING,
      null
    );
    notification.addAction(new ResolveAction(conflictingRepository));
    notification.addAction(CONTINUE_ACTION);
    notification.addAction(ABORT_ACTION);
    if (mySaver.wereChangesSaved()) notification.addAction(VIEW_STASH_ACTION);
    myNotifier.notify(notification);
  }

  @NotNull
  private ResolveConflictResult showConflictResolver(@NotNull GitRepository conflicting, boolean calledFromNotification) {
    GitConflictResolver.Params params = new GitConflictResolver
      .Params(myProject)
      .setMergeDialogCustomizer(createDialogCustomizer(conflicting, myRebaseSpec))
      .setReverse(true);
    RebaseConflictResolver conflictResolver = new RebaseConflictResolver(myProject, conflicting, params, calledFromNotification);
    boolean allResolved = conflictResolver.merge();
    if (conflictResolver.myWasNothingToMerge) return ResolveConflictResult.NOTHING_TO_MERGE;
    if (allResolved) return ResolveConflictResult.ALL_RESOLVED;
    return ResolveConflictResult.UNRESOLVED_REMAIN;
  }

  @Nullable
  private static Hash resolveRef(@NotNull GitRepository repository, @NotNull String ref) {
    GitRevisionNumber resolved = null;
    try {
      resolved = GitRevisionNumber.resolve(repository.getProject(), repository.getRoot(), ref);
    }
    catch (VcsException e) {
      LOG.warn(e);
    }
    return resolved != null ? HashImpl.build(resolved.asString()) : null;
  }

  @NotNull
  private static MergeDialogCustomizer createDialogCustomizer(@NotNull GitRepository repository, @NotNull GitRebaseSpec rebaseSpec) {
    GitRebaseParams rebaseParams = rebaseSpec.getParams();
    if (rebaseParams != null) {
      String currentBranchAtTheStartOfRebase = rebaseSpec.getInitialBranchNames().get(repository);
      String upstream = rebaseParams.getUpstream();
      if (upstream.equals(HEAD)) {
          /* this is to overcome a hack: passing HEAD into `git rebase HEAD branch`
             to avoid passing branch names for different repositories */
        upstream = currentBranchAtTheStartOfRebase;
      }
      String branch = rebaseParams.getBranch();
      if (branch == null) {
        branch = currentBranchAtTheStartOfRebase;
      }

      if (upstream != null && branch != null) {
        Hash rebaseHead = resolveRef(repository, REBASE_HEAD);
        Hash mergeBase = null;
        try {
          GitRevisionNumber mergeBaseRev = GitHistoryUtils.getMergeBase(repository.getProject(), repository.getRoot(), upstream, branch);
          mergeBase = mergeBaseRev != null ? HashImpl.build(mergeBaseRev.getRev()) : null;
        }
        catch (VcsException e) {
          LOG.warn(e);
        }
        return new GitRebaseMergeDialogCustomizer(repository, upstream, branch, rebaseHead, mergeBase);
      }
    }
    return new GitDefaultMergeDialogCustomizer(repository.getProject());
  }

  private static final class GitRebaseMergeDialogCustomizer extends MergeDialogCustomizer {
    @NotNull private final GitRepository myRepository;
    @NotNull private final String myRebasingBranch;
    @NotNull private final String myBasePresentable;
    @Nullable private final String myBaseBranch;
    @Nullable private final Hash myBaseHash;
    @Nullable private final Hash myIngoingCommit;
    @Nullable private final Hash myMergeBase;

    private GitRebaseMergeDialogCustomizer(@NotNull GitRepository repository,
                                           @NotNull String upstream,
                                           @NotNull String branch,
                                           @Nullable Hash ingoingCommit,
                                           @Nullable Hash mergeBase) {
      myRepository = repository;
      myRebasingBranch = branch;
      myIngoingCommit = ingoingCommit;
      myMergeBase = mergeBase;
      if (upstream.matches("[a-fA-F0-9]{40}")) {
        myBasePresentable = VcsLogUtil.getShortHash(upstream);
        myBaseBranch = null;
        myBaseHash = HashImpl.build(upstream);
      }
      else {
        myBasePresentable = upstream;
        myBaseBranch = upstream;
        myBaseHash = null;
      }
    }

    @NotNull
    @Override
    public String getMultipleFileMergeDescription(@NotNull Collection<VirtualFile> files) {
      return GitDefaultMergeDialogCustomizerKt.getDescriptionForRebase(myRebasingBranch, myBaseBranch, myBaseHash);
    }

    @NotNull
    @Override
    public String getLeftPanelTitle(@NotNull VirtualFile file) {
      return GitDefaultMergeDialogCustomizerKt.getDefaultLeftPanelTitleForBranch(myRebasingBranch);
    }

    @NotNull
    @Override
    public String getRightPanelTitle(@NotNull VirtualFile file, @Nullable VcsRevisionNumber revisionNumber) {
      GitRevisionNumber gitRevisionNumber = tryCast(revisionNumber, GitRevisionNumber.class);
      Hash hash = gitRevisionNumber != null ? HashImpl.build(gitRevisionNumber.asString()) : myBaseHash;
      return GitDefaultMergeDialogCustomizerKt.getDefaultRightPanelTitleForBranch(myBaseBranch, hash);
    }

    @Nullable
    @Override
    public List<String> getColumnNames() {
      return asList(GitMergeProvider.calcColumnName(false, myRebasingBranch),
                    GitMergeProvider.calcColumnName(true, myBasePresentable));
    }

    @NotNull
    @Override
    public DiffEditorTitleCustomizerList getTitleCustomizerList(@NotNull FilePath file) {
      return new DiffEditorTitleCustomizerList(
        getLeftTitleCustomizer(file),
        null,
        getRightTitleCustomizer(file)
      );
    }

    @Nullable
    public DiffEditorTitleCustomizer getLeftTitleCustomizer(@NotNull FilePath file) {
      if (myIngoingCommit == null) {
        return null;
      }
      return getTitleWithCommitDetailsCustomizer(
        GitBundle.message("rebase.conflict.diff.dialog.left.title", myIngoingCommit.toShortString(), myRebasingBranch),
        myRepository,
        file,
        myIngoingCommit.asString()
      );
    }

    @Nullable
    public DiffEditorTitleCustomizer getRightTitleCustomizer(@NotNull FilePath file) {
      if (myMergeBase == null) {
        return null;
      }
      String title = myBaseBranch != null
                     ? GitBundle.message("rebase.conflict.diff.dialog.right.with.branch.title", myBaseBranch)
                     : GitBundle.getString("rebase.conflict.diff.dialog.right.simple.title");
      return getTitleWithCommitsRangeDetailsCustomizer(title, myRepository, file, new Pair<>(myMergeBase.asString(), HEAD));
    }
  }

  private void showStoppedForEditingMessage() {
    Notification notification = IMPORTANT_ERROR_NOTIFICATION.createNotification(
      GitBundle.getString("rebase.notification.editing.title"),
      "",
      NotificationType.INFORMATION,
      null
    );
    notification.addAction(CONTINUE_ACTION);
    notification.addAction(ABORT_ACTION);
    myNotifier.notify(notification);
  }

  private void showFatalError(@NotNull final String error,
                              @NotNull final GitRepository currentRepository,
                              boolean somethingWasRebased,
                              @NotNull final Collection<GitRepository> successful) {
    String repo = myRepositoryManager.moreThanOneRoot() ? getShortRepositoryName(currentRepository) + ": " : "";
    String description = repo + error + "<br/>" + GitRebaseUtils.mentionLocalChangesRemainingInStash(mySaver);
    String title = myRebaseSpec.getOngoingRebase() == null
                   ? GitBundle.getString("rebase.notification.failed.rebase.title")
                   : GitBundle.getString("rebase.notification.failed.continue.title");
    Notification notification = IMPORTANT_ERROR_NOTIFICATION.createNotification(
      title,
      description,
      NotificationType.ERROR,
      null
    );
    notification.addAction(RETRY_ACTION);
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
      GitBundle.getString("rebase.git.operation.name"),
      message,
      null,
      actions.toArray(new NotificationAction[0])
    );
  }

  @NotNull
  private static Map<GitRepository, GitSuccessfulRebase> getSuccessfulRepositories(@NotNull Map<GitRepository, GitRebaseStatus> statuses) {
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
      if (repository.getCurrentBranchName() == null) {
        LOG.error("No current branch in " + repository);
        return true;
      }
      String rebasingBranch = notNull(myRebaseSpec.getParams().getBranch(), repository.getCurrentBranchName());
      if (isRebasingPublishedCommit(repository, upstream, rebasingBranch)) {
        return askIfShouldRebasePublishedCommit();
      }
    }
    return true;
  }

  private boolean isRebasingPublishedCommit(@NotNull GitRepository repository,
                                            @NotNull String baseBranch,
                                            @NotNull String rebasingBranch) {
    try {
      List<? extends TimedVcsCommit> commits = GitHistoryUtils.collectTimedCommits(myProject, repository.getRoot(),
                                                                                   baseBranch + ".." + rebasingBranch);
      return exists(commits, commit -> GitProtectedBranchesKt.isCommitPublished(repository, commit.getId()));
    }
    catch (VcsException e) {
      LOG.error("Couldn't collect commits", e);
      return true;
    }
  }

  private static boolean askIfShouldRebasePublishedCommit() {
    Ref<Boolean> rebaseAnyway = Ref.create(false);
    ApplicationManager.getApplication().invokeAndWait(() -> {
      int answer = DialogManager.showMessage(
        GitBundle.getString("rebase.confirmation.dialog.published.commits.message"),
        GitBundle.getString("rebase.confirmation.dialog.published.commits.title"),
        new String[]{
          GitBundle.getString("rebase.confirmation.dialog.published.commits.button.rebase.text"),
          GitBundle.getString("rebase.confirmation.dialog.published.commits.button.cancel.text")
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

    @CalledInBackground
    @Override
    protected boolean proceedAfterAllMerged() {
      if (myCalledFromNotification) {
        retry(GitBundle.getString("rebase.progress.indicator.continue.title"));
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

  private class ResolveAction extends NotificationAction {
    @NotNull private final GitRepository myCurrentRepository;

    ResolveAction(@NotNull GitRepository currentRepository) {
      super(GitBundle.messagePointer("action.NotificationAction.text.resolve"));
      myCurrentRepository = currentRepository;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
      myProgressManager.run(new Task.Backgroundable(
        myProject,
        GitBundle.getString("rebase.progress.indicator.conflicts.collecting.title")
      ) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          showConflictResolver(myCurrentRepository, true);
        }
      });
    }
  }

  private void abort() {
    myProgressManager.run(new Task.Backgroundable(myProject, GitBundle.getString("rebase.progress.indicator.aborting.title")) {
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
    @NonNls private static final Pattern REBASING_PATTERN = Pattern.compile("^Rebasing \\((\\d+)/(\\d+)\\)$");
    @NonNls private static final String APPLYING_PREFIX = "Applying: ";
    private int currentCommit = 0;

    private final int myCommitsToRebase;
    @NotNull private final ProgressIndicator myIndicator;

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
