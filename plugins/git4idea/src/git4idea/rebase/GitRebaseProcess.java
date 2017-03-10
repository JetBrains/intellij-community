/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.rebase;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.Repository;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.MultiMap;
import git4idea.GitUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.changes.GitChangeUtils;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitLineHandlerListener;
import git4idea.commands.GitUntrackedFilesOverwrittenByOperationDetector;
import git4idea.merge.GitConflictResolver;
import git4idea.rebase.GitSuccessfulRebase.SuccessType;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.stash.GitChangesSaver;
import git4idea.util.GitFreezingProcess;
import git4idea.util.GitUntrackedFilesHelper;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.*;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.openapi.vfs.VfsUtilCore.toVirtualFileArray;
import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.ObjectUtils.coalesce;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static com.intellij.util.containers.ContainerUtilRt.newArrayList;
import static git4idea.GitUtil.*;
import static java.util.Collections.singleton;

public class GitRebaseProcess {

  private static final Logger LOG = Logger.getInstance(GitRebaseProcess.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final ChangeListManager myChangeListManager;
  @NotNull private final VcsNotifier myNotifier;
  @NotNull private final GitRepositoryManager myRepositoryManager;

  @NotNull private final GitRebaseSpec myRebaseSpec;
  @Nullable private final GitRebaseResumeMode myCustomMode;
  @NotNull private final GitChangesSaver mySaver;
  @NotNull private final ProgressManager myProgressManager;

  public GitRebaseProcess(@NotNull Project project, @NotNull GitRebaseSpec rebaseSpec, @Nullable GitRebaseResumeMode customMode) {
    myProject = project;
    myRebaseSpec = rebaseSpec;
    myCustomMode = customMode;
    mySaver = rebaseSpec.getSaver();

    myGit = Git.getInstance();
    myChangeListManager = ChangeListManager.getInstance(myProject);
    myNotifier = VcsNotifier.getInstance(myProject);
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
    myProgressManager = ProgressManager.getInstance();
  }

  public void rebase() {
    new GitFreezingProcess(myProject, "rebase", this::doRebase).execute();
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

    Map<GitRepository, GitRebaseStatus> statuses = newLinkedHashMap(myRebaseSpec.getStatuses());
    List<GitRepository> repositoriesToRebase = myRepositoryManager.sortByDependency(myRebaseSpec.getIncompleteRepositories());
    AccessToken token = DvcsUtil.workingTreeChangeStarted(myProject);
    try {
      if (!saveDirtyRootsInitially(repositoriesToRebase)) return;

      GitRepository failed = null;
      for (GitRepository repository : repositoriesToRebase) {
        GitRebaseResumeMode customMode = null;
        if (repository == myRebaseSpec.getOngoingRebase()) {
          customMode = myCustomMode == null ? GitRebaseResumeMode.CONTINUE : myCustomMode;
        }

        Collection<Change> changes = collectFutureChanges(repository);

        GitRebaseStatus rebaseStatus = rebaseSingleRoot(repository, customMode, getSuccessfulRepositories(statuses));
        repository.update(); // make the repo state info actual ASAP
        statuses.put(repository, rebaseStatus);
        if (shouldBeRefreshed(rebaseStatus)) {
          refreshVfs(repository.getRoot(), changes);
        }
        if (rebaseStatus.getType() != GitRebaseStatus.Type.SUCCESS) {
          failed = repository;
          break;
        }
      }

      if (failed == null) {
        LOG.debug("Rebase completed successfully.");
        mySaver.load();
      }
      if (failed == null) {
        notifySuccess(getSuccessfulRepositories(statuses), getSkippedCommits(statuses));
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
    finally {
      token.finish();
    }
  }

  @Nullable
  private Collection<Change> collectFutureChanges(@NotNull GitRepository repository) {
    GitRebaseParams params = myRebaseSpec.getParams();
    if (params == null) return null;

    Collection<Change> changes = new ArrayList<>();
    String branch = params.getBranch();
    if (branch != null) {
      Collection<Change> changesFromCheckout = GitChangeUtils.getDiff(repository, HEAD, branch);
      if (changesFromCheckout == null) return null;
      changes.addAll(changesFromCheckout);
    }

    String rev1 = coalesce(params.getNewBase(), branch, HEAD);
    String rev2 = params.getUpstream();
    Collection<Change> changesFromRebase = GitChangeUtils.getDiff(repository, rev1, rev2);
    if (changesFromRebase == null) return null;

    changes.addAll(changesFromRebase);
    return changes;
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
                                           @NotNull Map<GitRepository, GitSuccessfulRebase> alreadyRebased) {
    VirtualFile root = repository.getRoot();
    String repoName = getShortRepositoryName(repository);
    LOG.info("Rebasing root " + repoName + ", mode: " + notNull(customMode, "standard"));

    Collection<GitRebaseUtils.CommitInfo> skippedCommits = newArrayList();
    MultiMap<GitRepository, GitRebaseUtils.CommitInfo> allSkippedCommits = getSkippedCommits(alreadyRebased);
    boolean retryWhenDirty = false;

    while (true) {
      GitRebaseProblemDetector rebaseDetector = new GitRebaseProblemDetector();
      GitUntrackedFilesOverwrittenByOperationDetector untrackedDetector = new GitUntrackedFilesOverwrittenByOperationDetector(root);
      GitRebaseLineListener progressListener = new GitRebaseLineListener();
      GitCommandResult result = callRebase(repository, customMode, rebaseDetector, untrackedDetector, progressListener);

      boolean somethingRebased = customMode != null || progressListener.getResult().current > 1;

      if (result.success()) {
        if (rebaseDetector.hasStoppedForEditing()) {
          showStoppedForEditingMessage(repository);
          return new GitRebaseStatus(GitRebaseStatus.Type.SUSPENDED, skippedCommits);
        }
        LOG.debug("Successfully rebased " + repoName);
        return GitSuccessfulRebase.parseFromOutput(result.getOutput(), skippedCommits);
      }
      else if (result.cancelled()) {
        LOG.info("Rebase was cancelled");
        throw new ProcessCanceledException();
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
          LOG.warn("Couldn't " + mySaver.getOperationName() + " root " + repository.getRoot() + ": " + saveError);
          showFatalError(saveError, repository, somethingRebased, alreadyRebased.keySet(), allSkippedCommits);
          GitRebaseStatus.Type type = somethingRebased ? GitRebaseStatus.Type.SUSPENDED : GitRebaseStatus.Type.ERROR;
          return new GitRebaseStatus(type, skippedCommits);
        }
      }
      else if (untrackedDetector.wasMessageDetected()) {
        LOG.info("Untracked files detected in " + repoName);
        showUntrackedFilesError(untrackedDetector.getRelativeFilePaths(), repository, somethingRebased, alreadyRebased.keySet(),
                                allSkippedCommits);
        GitRebaseStatus.Type type = somethingRebased ? GitRebaseStatus.Type.SUSPENDED : GitRebaseStatus.Type.ERROR;
        return new GitRebaseStatus(type, skippedCommits);
      }
      else if (rebaseDetector.isNoChangeError()) {
        LOG.info("'No changes' situation detected in " + repoName);
        GitRebaseUtils.CommitInfo currentRebaseCommit = GitRebaseUtils.getCurrentRebaseCommit(myProject, root);
        if (currentRebaseCommit != null) skippedCommits.add(currentRebaseCommit);
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
          showFatalError(result.getErrorOutputAsHtmlString(), repository, somethingRebased, alreadyRebased.keySet(), allSkippedCommits);
          GitRebaseStatus.Type type = somethingRebased ? GitRebaseStatus.Type.SUSPENDED : GitRebaseStatus.Type.ERROR;
          return new GitRebaseStatus(type, skippedCommits);
        }
        else {
          notifyNotAllConflictsResolved(repository, allSkippedCommits);
          return new GitRebaseStatus(GitRebaseStatus.Type.SUSPENDED, skippedCommits);
        }
      }
      else {
        LOG.info("Error rebasing root " + repoName + ": " + result.getErrorOutputAsJoinedString());
        showFatalError(result.getErrorOutputAsHtmlString(), repository, somethingRebased, alreadyRebased.keySet(), allSkippedCommits);
        GitRebaseStatus.Type type = somethingRebased ? GitRebaseStatus.Type.SUSPENDED : GitRebaseStatus.Type.ERROR;
        return new GitRebaseStatus(type, skippedCommits);
      }
    }
  }

  @NotNull
  private GitCommandResult callRebase(@NotNull GitRepository repository,
                                      @Nullable GitRebaseResumeMode mode,
                                      @NotNull GitLineHandlerListener... listeners) {
    if (mode == null) {
      GitRebaseParams params = assertNotNull(myRebaseSpec.getParams());
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

  private boolean shouldBeRefreshed(@NotNull GitRebaseStatus rebaseStatus) {
    return rebaseStatus.getType() != GitRebaseStatus.Type.SUCCESS ||
           shouldRefreshOnSuccess(((GitSuccessfulRebase)rebaseStatus).getSuccessType());
  }

  protected boolean shouldRefreshOnSuccess(@NotNull SuccessType successType) {
    return successType != SuccessType.UP_TO_DATE;
  }

  private static void refresh(@NotNull Collection<GitRepository> repositories) {
    GitUtil.updateRepositories(repositories);
    // TODO use --diff-stat, and refresh only what's needed
    VfsUtil.markDirtyAndRefresh(false, true, false, toVirtualFileArray(getRootsFromRepositories(repositories)));
  }

  private boolean saveDirtyRootsInitially(@NotNull List<GitRepository> repositories) {
    Collection<GitRepository> repositoriesToSave = filter(repositories, repository -> {
      return !repository.equals(myRebaseSpec.getOngoingRebase()); // no need to save anything when --continue/--skip is to be called
    });
    if (repositoriesToSave.isEmpty()) return true;
    Collection<VirtualFile> rootsToSave = getRootsFromRepositories(getDirtyRoots(repositoriesToSave));
    String error = saveLocalChanges(rootsToSave);
    if (error != null) {
      myNotifier.notifyError("Rebase not Started", error);
      return false;
    }
    return true;
  }

  @Nullable
  private String saveLocalChanges(@NotNull Collection<VirtualFile> rootsToSave) {
    try {
      mySaver.saveLocalChanges(rootsToSave);
      return null;
    }
    catch (VcsException e) {
      LOG.warn(e);
      return "Couldn't " + mySaver.getSaverName() + " local uncommitted changes:<br/>" + e.getMessage();
    }
  }

  private Collection<GitRepository> findRootsWithLocalChanges(@NotNull Collection<GitRepository> repositories) {
    return filter(repositories, repository -> myChangeListManager.haveChangesUnder(repository.getRoot()) != ThreeState.NO);
  }

  protected void notifySuccess(@NotNull Map<GitRepository, GitSuccessfulRebase> successful,
                             @NotNull MultiMap<GitRepository, GitRebaseUtils.CommitInfo> skippedCommits) {
    String rebasedBranch = getCommonCurrentBranchNameIfAllTheSame(myRebaseSpec.getAllRepositories());
    List<SuccessType> successTypes = map(successful.values(), GitSuccessfulRebase::getSuccessType);
    SuccessType commonType = getItemIfAllTheSame(successTypes, SuccessType.REBASED);
    GitRebaseParams params = myRebaseSpec.getParams();
    String baseBranch = params == null ? null : notNull(params.getNewBase(), params.getUpstream());
    if ("HEAD".equals(baseBranch)) {
      baseBranch = getItemIfAllTheSame(myRebaseSpec.getInitialBranchNames().values(), baseBranch);
    }
    String message = commonType.formatMessage(rebasedBranch, baseBranch, params != null && params.getBranch() != null);
    message += mentionSkippedCommits(skippedCommits);
    myNotifier.notifyMinorInfo("Rebase Successful", message, new NotificationListener.Adapter() {
      @Override
      protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
        handlePossibleCommitLinks(e.getDescription(), skippedCommits);
      }
    });
  }

  @Nullable
  private static String getCommonCurrentBranchNameIfAllTheSame(@NotNull Collection<GitRepository> repositories) {
    return getItemIfAllTheSame(map(repositories, Repository::getCurrentBranchName), null);
  }

  @Contract("_, !null -> !null")
  private static <T> T getItemIfAllTheSame(@NotNull Collection<T> collection, @Nullable T defaultItem) {
    return newHashSet(collection).size() == 1 ? getFirstItem(collection) : defaultItem;
  }

  private void notifyNotAllConflictsResolved(@NotNull GitRepository conflictingRepository,
                                             MultiMap<GitRepository, GitRebaseUtils.CommitInfo> skippedCommits) {
    String description = "You have to <a href='resolve'>resolve</a> the conflicts and <a href='continue'>continue</a> rebase.<br/>" +
                         "If you want to start from the beginning, you can <a href='abort'>abort</a> rebase.";
    description += GitRebaseUtils.mentionLocalChangesRemainingInStash(mySaver);
    myNotifier.notifyImportantWarning("Rebase Suspended", description,
                                      new RebaseNotificationListener(conflictingRepository, skippedCommits));
  }

  @NotNull
  private ResolveConflictResult showConflictResolver(@NotNull GitRepository conflicting, boolean calledFromNotification) {
    GitConflictResolver.Params params = new GitConflictResolver.Params().setReverse(true);
    RebaseConflictResolver conflictResolver = new RebaseConflictResolver(myProject, myGit, conflicting, params, calledFromNotification);
    boolean allResolved = conflictResolver.merge();
    if (conflictResolver.myWasNothingToMerge) return ResolveConflictResult.NOTHING_TO_MERGE;
    if (allResolved) return ResolveConflictResult.ALL_RESOLVED;
    return ResolveConflictResult.UNRESOLVED_REMAIN;
  }

  private void showStoppedForEditingMessage(@NotNull GitRepository repository) {
    String description = "Once you are satisfied with your changes you may <a href='continue'>continue</a>";
    myNotifier.notifyImportantInfo("Rebase Stopped for Editing", description, new RebaseNotificationListener(repository, MultiMap.empty()));
  }

  private void showFatalError(@NotNull final String error,
                              @NotNull final GitRepository currentRepository,
                              boolean somethingWasRebased,
                              @NotNull final Collection<GitRepository> successful,
                              @NotNull MultiMap<GitRepository, GitRebaseUtils.CommitInfo> skippedCommits) {
    String repo = myRepositoryManager.moreThanOneRoot() ? getShortRepositoryName(currentRepository) + ": " : "";
    String description = repo + error + "<br/>" +
                         mentionRetryAndAbort(somethingWasRebased, successful) +
                         mentionSkippedCommits(skippedCommits) +
                         GitRebaseUtils.mentionLocalChangesRemainingInStash(mySaver);
    String title = myRebaseSpec.getOngoingRebase() == null ? "Rebase Failed" : "Continue Rebase Failed";
    myNotifier.notifyError(title, description, new RebaseNotificationListener(currentRepository, skippedCommits));
  }

  private void showUntrackedFilesError(@NotNull Set<String> untrackedPaths,
                                       @NotNull GitRepository currentRepository,
                                       boolean somethingWasRebased,
                                       @NotNull Collection<GitRepository> successful,
                                       MultiMap<GitRepository, GitRebaseUtils.CommitInfo> skippedCommits) {
    String message = GitUntrackedFilesHelper.createUntrackedFilesOverwrittenDescription("rebase", true) +
                     mentionRetryAndAbort(somethingWasRebased, successful) +
                     mentionSkippedCommits(skippedCommits) +
                     GitRebaseUtils.mentionLocalChangesRemainingInStash(mySaver);
    GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(myProject, currentRepository.getRoot(), untrackedPaths, "rebase", message);
  }

  @NotNull
  private static String mentionRetryAndAbort(boolean somethingWasRebased, @NotNull Collection<GitRepository> successful) {
    return somethingWasRebased || !successful.isEmpty()
           ? "You can <a href='retry'>retry</a> or <a href='abort'>abort</a> rebase."
           : "<a href='retry'>Retry.</a>";
  }

  @NotNull
  private static String mentionSkippedCommits(@NotNull MultiMap<GitRepository, GitRebaseUtils.CommitInfo> skippedCommits) {
    if (skippedCommits.isEmpty()) return "";
    String message = "<br/>";
    if (skippedCommits.values().size() == 1) {
      message += "The following commit was skipped during rebase:<br/>";
    }
    else {
      message += "The following commits were skipped during rebase:<br/>";
    }
    message += StringUtil.join(skippedCommits.values(), commitInfo -> {
      String commitMessage = StringUtil.shortenPathWithEllipsis(commitInfo.subject, 72, true);
      String hash = commitInfo.revision.asString();
      String shortHash = DvcsUtil.getShortHash(commitInfo.revision.asString());
      return String.format("<a href='%s'>%s</a> %s", hash, shortHash, commitMessage);
    }, "<br/>");
    return message;
  }

  @NotNull
  private static MultiMap<GitRepository, GitRebaseUtils.CommitInfo> getSkippedCommits(@NotNull Map<GitRepository, ? extends GitRebaseStatus> statuses) {
    MultiMap<GitRepository, GitRebaseUtils.CommitInfo> map = MultiMap.create();
    for (GitRepository repository : statuses.keySet()) {
      map.put(repository, statuses.get(repository).getSkippedCommits());
    }
    return map;
  }

  @NotNull
  private static Map<GitRepository, GitSuccessfulRebase> getSuccessfulRepositories(@NotNull Map<GitRepository, GitRebaseStatus> statuses) {
    Map<GitRepository, GitSuccessfulRebase> map = newLinkedHashMap();
    for (GitRepository repository : statuses.keySet()) {
      GitRebaseStatus status = statuses.get(repository);
      if (status instanceof GitSuccessfulRebase) map.put(repository, (GitSuccessfulRebase)status);
    }
    return map;
  }

  private class RebaseConflictResolver extends GitConflictResolver {
    private final boolean myCalledFromNotification;
    private boolean myWasNothingToMerge;

    RebaseConflictResolver(@NotNull Project project,
                           @NotNull Git git,
                           @NotNull GitRepository repository,
                           @NotNull Params params, boolean calledFromNotification) {
      super(project, git, singleton(repository.getRoot()), params);
      myCalledFromNotification = calledFromNotification;
    }

    @Override
    protected void notifyUnresolvedRemain() {
      // will be handled in the common notification
    }

    @CalledInBackground
    @Override
    protected boolean proceedAfterAllMerged() throws VcsException {
      if (myCalledFromNotification) {
        retry(GitRebaseUtils.CONTINUE_PROGRESS_TITLE);
      }
      return true;
    }

    @Override
    protected boolean proceedIfNothingToMerge() throws VcsException {
      myWasNothingToMerge = true;
      return true;
    }
  }

  private enum ResolveConflictResult {
    ALL_RESOLVED,
    NOTHING_TO_MERGE,
    UNRESOLVED_REMAIN
  }

  private class RebaseNotificationListener extends NotificationListener.Adapter {
    @NotNull private final GitRepository myCurrentRepository;
    @NotNull private final MultiMap<GitRepository, GitRebaseUtils.CommitInfo> mySkippedCommits;

    RebaseNotificationListener(@NotNull GitRepository currentRepository,
                               @NotNull MultiMap<GitRepository, GitRebaseUtils.CommitInfo> skippedCommits) {
      myCurrentRepository = currentRepository;
      mySkippedCommits = skippedCommits;
    }

    @Override
    protected void hyperlinkActivated(@NotNull Notification notification, @NotNull final HyperlinkEvent e) {
      final String href = e.getDescription();
      if ("abort".equals(href)) {
        abort();
      }
      else if ("continue".equals(href)) {
        retry(GitRebaseUtils.CONTINUE_PROGRESS_TITLE);
      }
      else if ("retry".equals(href)) {
        retry("Retry Rebase Process...");
      }
      else if ("resolve".equals(href)) {
        showConflictResolver(myCurrentRepository, true);
      }
      else if ("stash".equals(href)) {
        mySaver.showSavedChanges();
      }
      else {
        handlePossibleCommitLinks(href, mySkippedCommits);
      }
    }
  }

  private void abort() {
    myProgressManager.run(new Task.Backgroundable(myProject, "Aborting Rebase Process...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        GitRebaseUtils.abort(myProject, indicator);
      }
    });
  }

  private void retry(@NotNull String processTitle) {
    myProgressManager.run(new Task.Backgroundable(myProject, processTitle, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        GitRebaseUtils.continueRebase(myProject);
      }
    });
  }

  private void handlePossibleCommitLinks(@NotNull String href, @NotNull MultiMap<GitRepository, GitRebaseUtils.CommitInfo> skippedCommits) {
    GitRepository repository = findRootBySkippedCommit(href, skippedCommits);
    if (repository != null) {
      GitUtil.showSubmittedFiles(myProject, href, repository.getRoot(), true, false);
    }
  }

  @Nullable
  private static GitRepository findRootBySkippedCommit(@NotNull final String hash,
                                                       @NotNull MultiMap<GitRepository, GitRebaseUtils.CommitInfo> skippedCommits) {
    return find(skippedCommits.keySet(),  repository-> exists(skippedCommits.get(repository),  info-> info.revision.asString().equals(hash)));
  }
}
