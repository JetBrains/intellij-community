// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.branch.DvcsSyncSettings;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.impl.LocalChangesUnderRoots;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.branch.GitBranchPair;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.Git;
import git4idea.config.GitVcsSettings;
import git4idea.config.UpdateMethod;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.merge.GitMergeCommittingConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.rebase.GitRebaseUtils;
import git4idea.rebase.GitRebaser;
import git4idea.repo.*;
import git4idea.util.GitPreservingProcess;
import org.jetbrains.annotations.*;

import java.util.*;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static git4idea.GitNotificationIdsHolder.*;
import static git4idea.GitUtil.getRootsFromRepositories;
import static git4idea.GitUtil.mention;
import static git4idea.fetch.GitFetchSupport.fetchSupport;
import static git4idea.util.GitUIUtil.code;

/**
 * Handles update process (pull via merge or rebase) for several roots.
 *
 * The class is not thread-safe and is stateful. It is intended to be used only once.
 */
@ApiStatus.Internal
public final class GitUpdateProcess {
  private static final Logger LOG = Logger.getInstance(GitUpdateProcess.class);

  private final @NotNull Project myProject;
  private final @NotNull Git myGit;

  private final @Unmodifiable @NotNull List<GitRepository> myRepositories;
  private final @NotNull Map<GitRepository, GitSubmodule> mySubmodulesInDetachedHead;
  private final boolean myCheckRebaseOverMergeProblem;
  private final boolean myCheckForTrackedBranchExistence;
  private final UpdatedFiles myUpdatedFiles;
  private final Map<GitRepository, GitBranchPair> myUpdateConfig;
  private final @NotNull ProgressIndicator myProgressIndicator;
  private final @NotNull GitMerger myMerger;

  private final @NotNull Map<GitRepository, @Nls String> mySkippedRoots = new LinkedHashMap<>();
  private @Nullable Map<GitRepository, HashRange> myUpdatedRanges;

  public GitUpdateProcess(@NotNull Project project,
                          @Nullable ProgressIndicator progressIndicator,
                          @NotNull @Unmodifiable Collection<GitRepository> repositories,
                          @NotNull UpdatedFiles updatedFiles,
                          @Nullable Map<GitRepository, GitBranchPair> updateConfig,
                          boolean checkRebaseOverMergeProblem,
                          boolean checkForTrackedBranchExistence) {
    myProject = project;
    myCheckRebaseOverMergeProblem = checkRebaseOverMergeProblem;
    myCheckForTrackedBranchExistence = checkForTrackedBranchExistence;
    myGit = Git.getInstance();
    myUpdatedFiles = updatedFiles;
    myUpdateConfig = updateConfig;

    myRepositories = GitUtil.getRepositoryManager(project).sortByDependency(repositories);
    myProgressIndicator = progressIndicator == null ? new EmptyProgressIndicator() : progressIndicator;
    myMerger = new GitMerger(myProject);

    GitUtil.updateRepositories(repositories);

    mySubmodulesInDetachedHead = collectDetachedSubmodules(myRepositories);
  }

  private static @NotNull Map<GitRepository, GitSubmodule> collectDetachedSubmodules(@NotNull @Unmodifiable List<GitRepository> repositories) {
    Map<GitRepository, GitSubmodule> detachedSubmodules = new LinkedHashMap<>();
    for (GitRepository repository : repositories) {
      if (repository.isOnBranch()) continue;

      GitSubmodule submodule = GitSubmoduleKt.asSubmodule(repository);
      if (submodule != null) {
        detachedSubmodules.put(repository, submodule);
      }
    }
    return detachedSubmodules;
  }

  /**
   * Checks if update is possible, saves local changes and updates all roots.
   * In case of error shows notification and returns false. If update completes without errors, returns true.
   *
   * Perform update on all roots.
   * 0. Blocks reloading project on external change, saving/syncing on frame deactivation.
   * 1. Checks if update is possible (rebase/merge in progress, no tracked branches...) and provides merge dialog to solve problems.
   * 2. Finds updaters to use (merge or rebase).
   * 3. Preserves local changes if needed (not needed for merge sometimes).
   * 4. Updates via 'git pull' or equivalent.
   * 5. Restores local changes if update completed or failed with error. If update is incomplete, i.e. some unmerged files remain,
   * local changes are not restored.
   *
   */
  public @NotNull GitUpdateResult update(final UpdateMethod updateMethod) {
    LOG.info("update started|" + updateMethod);
    String oldText = myProgressIndicator.getText();
    myProgressIndicator.setText(GitBundle.message("update.process.progress.title"));

    // check if update is possible
    if (isUpdateNotReady()) {
      return GitUpdateResult.NOT_READY;
    }

    Map<GitRepository, GitBranchPair> trackedBranches = myUpdateConfig != null ? myUpdateConfig : checkTrackedBranchesConfiguration();
    if (ContainerUtil.isEmpty(trackedBranches)) {
      return GitUpdateResult.NOT_READY;
    }

    if (!fetchAndNotify(trackedBranches)) {
      return GitUpdateResult.NOT_READY;
    }

    GitUpdateResult result;
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, VcsBundle.message("activity.name.update"))) {
      result = updateImpl(updateMethod);
    }
    myProgressIndicator.setText(oldText);
    return result;
  }

  public boolean isUpdateNotReady() {
    return checkRebaseInProgress() || isMergeInProgress() || areUnmergedFiles();
  }

  private @NotNull GitUpdateResult updateImpl(@NotNull UpdateMethod updateMethod) {
    // re-read after fetch, remote branch might have been deleted
    Map<GitRepository, GitBranchPair> trackedBranches = myUpdateConfig != null ? myUpdateConfig : checkTrackedBranchesConfiguration();
    if (trackedBranches == null) {
      return GitUpdateResult.NOT_READY;
    }

    Map<GitRepository, GitUpdater> updaters;
    try {
      updaters = defineUpdaters(updateMethod, trackedBranches);
    }
    catch (VcsException e) {
      LOG.info(e);
      VcsNotifier.getInstance(myProject)
        .notifyError(UPDATE_ERROR, GitBundle.message("notification.title.update.failed"),
                     e.getMessage(),
                     Collections.singleton(e)
        );
      return GitUpdateResult.ERROR;
    }

    if (updaters.isEmpty()) {
      return GitUpdateResult.NOTHING_TO_UPDATE;
    }

    GitUpdatedRanges updatedRanges = GitUpdatedRanges.calcInitialPositions(myProject, trackedBranches);

    try {
      updaters = tryFastForwardMergeForRebaseUpdaters(updaters);

      if (updaters.isEmpty()) {
        // everything was updated via the fast-forward merge
        return GitUpdateResult.SUCCESS;
      }

      if (myCheckRebaseOverMergeProblem) {
        Collection<GitRepository> problematicRoots = findRootsRebasingOverMerge(updaters);
        if (!problematicRoots.isEmpty()) {
          GitRebaseOverMergeProblem.Decision decision = GitRebaseOverMergeProblem.showDialog();
          if (decision == GitRebaseOverMergeProblem.Decision.MERGE_INSTEAD) {
            for (GitRepository repo : problematicRoots) {
              GitBranchPair branchAndTracked = trackedBranches.get(repo);
              if (branchAndTracked == null) {
                LOG.error("No tracked branch information for root " + repo.getRoot());
                continue;
              }
              updaters.put(repo, new GitMergeUpdater(myProject, myGit, repo, branchAndTracked, myProgressIndicator, myUpdatedFiles));
            }
          }
          else if (decision == GitRebaseOverMergeProblem.Decision.CANCEL_OPERATION) {
            return GitUpdateResult.CANCEL;
          }
        }
      }

      // save local changes if needed (update via merge may perform without saving).
      final Collection<VirtualFile> myRootsToSave = new ArrayList<>();
      LOG.info("updateImpl: identifying if save is needed...");
      for (Map.Entry<GitRepository, GitUpdater> entry : updaters.entrySet()) {
        GitRepository repo = entry.getKey();
        GitUpdater updater = entry.getValue();
        if (updater.isSaveNeeded()) {
          myRootsToSave.add(repo.getRoot());
          LOG.info("update| root " + repo + " needs save");
        }
      }

      LOG.info("updateImpl: saving local changes...");
      final Ref<Boolean> incomplete = Ref.create(false);
      final Ref<GitUpdateResult> compoundResult = Ref.create();
      final Map<GitRepository, GitUpdater> finalUpdaters = updaters;
      new GitPreservingProcess(myProject, myGit, myRootsToSave, GitBundle.message("git.update.operation"),
                               GitBundle.message("progress.update.destination.remote"),
                               GitVcsSettings.getInstance(myProject).getSaveChangesPolicy(), myProgressIndicator, false, () -> {
        LOG.info("updateImpl: updating...");
        GitRepository currentlyUpdatedRoot = null;
        try {
          for (GitRepository repo : finalUpdaters.keySet()) {
            GitUpdater updater = finalUpdaters.get(repo);
            if (updater == null) continue;
            currentlyUpdatedRoot = repo;
            GitUpdateResult res = updater.update();
            LOG.info("updating root " + currentlyUpdatedRoot + " finished: " + res);
            if (res == GitUpdateResult.INCOMPLETE) {
              incomplete.set(true);
            }
            compoundResult.set(joinResults(compoundResult.get(), res));
          }
        }
        catch (VcsException e) {
          String rootName = (currentlyUpdatedRoot == null) ? "" : getShortRepositoryName(currentlyUpdatedRoot);
          LOG.info("Error updating changes for root " + currentlyUpdatedRoot, e);
          VcsNotifier.getInstance(myProject)
                                     .notifyError(UPDATE_ERROR, GitBundle.message("notification.title.error.updating.root", rootName),
                               GitBundle.message("notification.content.updating.root.failed.with.error", rootName,
                                                 e.getLocalizedMessage()));
        }
      }).execute(() -> {
        // Note: compoundResult normally should not be null, because the updaters map was checked for non-emptiness.
        // But if updater.update() fails with exception for the first root, then the value would not be assigned.
        // In this case we don't restore local changes either, because update failed.
        return !incomplete.get() && !compoundResult.isNull() && compoundResult.get().isSuccess();
      });
      // GitPreservingProcess#save may fail due index.lock presence
      return ObjectUtils.notNull(compoundResult.get(), GitUpdateResult.ERROR);
    }
    finally {
      myUpdatedRanges = updatedRanges.calcCurrentPositions();
    }
  }

  private @NotNull Collection<GitRepository> findRootsRebasingOverMerge(@NotNull Map<GitRepository, GitUpdater> updaters) {
    return ContainerUtil.mapNotNull(updaters.keySet(), repo -> {
      GitUpdater updater = updaters.get(repo);
      if (updater instanceof GitRebaseUpdater) {
        GitBranchPair sourceAndTarget = ((GitRebaseUpdater)updater).getSourceAndTarget();
        String currentRef = sourceAndTarget.getSource().getFullName();
        String baseRef = sourceAndTarget.getTarget().getFullName();
        return GitRebaseOverMergeProblem.hasProblem(myProject, repo.getRoot(), baseRef, currentRef) ? repo : null;
      }
      return null;
    });
  }

  private @NotNull Map<GitRepository, GitUpdater> tryFastForwardMergeForRebaseUpdaters(@NotNull Map<GitRepository, GitUpdater> updaters) {
    Map<GitRepository, GitUpdater> modifiedUpdaters = new LinkedHashMap<>();
    Map<VirtualFile, Collection<Change>> changesUnderRoots = LocalChangesUnderRoots.getChangesUnderRoots(getRootsFromRepositories(updaters.keySet()), myProject);
    for (GitRepository repository : updaters.keySet()) {
      GitUpdater updater = updaters.get(repository);
      Collection<Change> changes = changesUnderRoots.get(repository.getRoot());
      LOG.debug("Changes under root '" + getShortRepositoryName(repository) + "': " + changes);
      if (updater instanceof GitRebaseUpdater rebaseUpdater && changes != null && !changes.isEmpty()) {
        // check only if there are local changes, otherwise stash won't happen anyway and there would be no optimization
        if (rebaseUpdater.fastForwardMerge()) {
          continue;
        }
      }
      modifiedUpdaters.put(repository, updater);
    }
    return modifiedUpdaters;
  }

  private @NotNull Map<GitRepository, GitUpdater> defineUpdaters(@NotNull UpdateMethod updateMethod,
                                                                 @NotNull Map<GitRepository, GitBranchPair> trackedBranches) throws VcsException {
    Map<GitRepository, GitUpdater> updaters = new LinkedHashMap<>();
    for (GitRepository repository : trackedBranches.keySet()) {
      GitBranchPair branchAndTracked = trackedBranches.get(repository);
      GitUpdater updater = GitUpdater.getUpdater(myProject, myGit, branchAndTracked, repository, myProgressIndicator, myUpdatedFiles,
                                                 updateMethod);
      if (updater.isUpdateNeeded(branchAndTracked)) {
        updaters.put(repository, updater);
      }
    }

    for (GitSubmodule submodule : mySubmodulesInDetachedHead.values()) {
      GitRepository submoduleRepository = submodule.getRepository();
      GitRepository parentRepository = submodule.getParent();
      if (mySubmodulesInDetachedHead.containsKey(parentRepository)) continue; // updated recursively

      GitUpdater updater = new GitSubmoduleUpdater(myProject, myGit, parentRepository, submoduleRepository,
                                                   myProgressIndicator, myUpdatedFiles);
      updaters.put(submoduleRepository, updater);
    }

    LOG.info("Updaters: " + updaters);

    return updaters;
  }

  public @NotNull Map<GitRepository, String> getSkippedRoots() {
    return mySkippedRoots;
  }

  public @Nullable Map<GitRepository, HashRange> getUpdatedRanges() {
    return myUpdatedRanges;
  }

  private static @NotNull GitUpdateResult joinResults(@Nullable GitUpdateResult compoundResult, GitUpdateResult result) {
    if (compoundResult == null) {
      return result;
    }
    return compoundResult.join(result);
  }

  private boolean fetchAndNotify(@NotNull Map<GitRepository, GitBranchPair> updateConfig) {
    Collection<Pair<GitRepository, GitRemote>> remotes = ContainerUtil.mapNotNull(updateConfig.entrySet(), entry -> {
      GitRepository repository = entry.getKey();
      GitBranch target = entry.getValue().getTarget();
      if (target instanceof GitRemoteBranch) {
        return new Pair<>(repository, ((GitRemoteBranch)target).getRemote());
      }
      else {
        return null; // No need to fetch non-remote branches. Shouldn't happen, but typesafe fix breaks binary compatibility.
      }
    });
    return fetchSupport(myProject).fetchRemotes(remotes)
      .showNotificationIfFailed(GitBundle.message("notification.title.update.failed"));
  }

  /**
   * For each root check that the repository is on branch, and this branch is tracking a remote branch, and the remote branch exists.
   * If it is not true for at least one of roots, notify and return null.
   * If branch configuration is OK for all roots, return the collected tracking branch information.
   */
  private @Nullable Map<GitRepository, GitBranchPair> checkTrackedBranchesConfiguration() {
    LOG.info("checking tracked branch configuration...");

    Map<GitRepository, GitLocalBranch> currentBranches = new LinkedHashMap<>();
    List<GitRepository> detachedHeads = new ArrayList<>();
    for (GitRepository repository : myRepositories) {
      if (mySubmodulesInDetachedHead.containsKey(repository)) {
        LOG.debug("Repository " + repository + " is a submodule in detached HEAD state, not checking its tracked branch");
        continue;
      }

      GitLocalBranch branch = repository.getCurrentBranch();
      if (branch != null) {
        currentBranches.put(repository, branch);
      }
      else {
        detachedHeads.add(repository);
        LOG.info(String.format("skipping update of [%s] (detached HEAD)", getShortRepositoryName(repository)));
      }
    }

    if (!detachedHeads.isEmpty() && (currentBranches.isEmpty() || isSyncControl())) {
      notifyDetachedHeadError(detachedHeads.get(0));
      return null;
    }
    else {
      for (GitRepository repo : detachedHeads) {
        mySkippedRoots.put(repo, GitBundle.message("update.skip.root.reason.detached.head"));
      }
    }

    Map<GitRepository, GitBranchPair> trackedBranches = new LinkedHashMap<>();
    List<GitRepository> noTrackedBranch = new ArrayList<>();
    for (GitRepository repository: currentBranches.keySet()) {
      GitLocalBranch branch = currentBranches.get(repository);
      GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfoForBranch(repository, branch);
      if (trackInfo != null) {
        trackedBranches.put(repository, new GitBranchPair(branch, trackInfo.getRemoteBranch()));
      }
      else {
        noTrackedBranch.add(repository);
        LOG.info(String.format("skipping update of [%s] (no tracked branch for current branch [%s])",
                               getShortRepositoryName(repository), branch));
      }
    }

    if (myCheckForTrackedBranchExistence &&
        !noTrackedBranch.isEmpty() && (trackedBranches.isEmpty() || isSyncControl())) {
      GitRepository repo = noTrackedBranch.get(0);
      notifyNoTrackedBranchError(repo, currentBranches.get(repo));
      return null;
    }
    else {
      for (GitRepository repo : noTrackedBranch) {
        mySkippedRoots.put(repo, GitBundle.message("update.skip.root.reason.no.tracked.branch"));
      }
    }

    return trackedBranches;
  }

  private void notifyNoTrackedBranchError(@NotNull GitRepository repository, @NotNull GitLocalBranch currentBranch) {
    VcsNotifier.getInstance(repository.getProject())
      .notifyError(
        UPDATE_NO_TRACKED_BRANCH, GitBundle.message("update.notification.update.error"),
        getNoTrackedBranchError(repository, currentBranch.getName()),
        NotificationAction.createSimple(
          GitBundle.message("update.notification.choose.upstream.branch"),
          () -> {
            showUpdateDialog(repository);
          })
      );
  }

  private void showUpdateDialog(@NotNull GitRepository repository) {
    FixTrackedBranchDialog updateDialog = new FixTrackedBranchDialog(repository.getProject());

    if (updateDialog.showAndGet()) {
      new GitUpdateExecutionProcess(repository.getProject(),
                                    myRepositories,
                                    updateDialog.getUpdateConfig(),
                                    updateDialog.getUpdateMethod(),
                                    updateDialog.shouldSetAsTrackedBranch())
        .execute();
    }
  }

  private static void notifyDetachedHeadError(@NotNull GitRepository repository) {
    VcsNotifier.getInstance(repository.getProject())
      .notifyError(UPDATE_DETACHED_HEAD_ERROR, GitBundle.message("notification.title.can.t.update.no.current.branch"),
                         getDetachedHeadErrorNotificationContent(repository));
  }

  @VisibleForTesting
  public static @NlsContexts.NotificationContent @NotNull String getDetachedHeadErrorNotificationContent(@NotNull GitRepository repository) {
    return GitBundle.message("notification.content.detached.state.in.root.checkout.branch", mention(repository));
  }

  private boolean isSyncControl() {
    return GitVcsSettings.getInstance(myProject).getSyncSetting() == DvcsSyncSettings.Value.SYNC;
  }

  @VisibleForTesting
  public static @NlsContexts.NotificationContent @NotNull String getNoTrackedBranchError(@NotNull GitRepository repository, @NotNull @NlsSafe String branchName) {
    return GitBundle.message("notification.content.branch.in.repo.has.no.tracked.branch", code(branchName), mention(repository));
  }

  /**
   * Check if merge is in progress, propose to resolve conflicts.
   * @return true if merge is in progress, which means that update can't continue.
   */
  private boolean isMergeInProgress() {
    LOG.info("isMergeInProgress: checking if there is an unfinished merge process...");
    final Collection<VirtualFile> mergingRoots = myMerger.getMergingRoots();
    if (mergingRoots.isEmpty()) {
      return false;
    }
    LOG.info("isMergeInProgress: roots with unfinished merge: " + mergingRoots);
    GitConflictResolver.Params params = new GitConflictResolver.Params(myProject);
    params.setErrorNotificationTitle(GitBundle.message("update.process.generic.error.title"));
    params.setMergeDescription(GitBundle.message("update.process.error.message.unfinished.merge"));
    return !new GitMergeCommittingConflictResolver(myProject, mergingRoots, params, false).merge();
  }

  /**
   * Checks if there are unmerged files (which may still be possible even if rebase or merge have finished)
   * @return true if there are unmerged files at
   */
  private boolean areUnmergedFiles() {
    LOG.info("areUnmergedFiles: checking if there are unmerged files...");
    GitConflictResolver.Params params = new GitConflictResolver.Params(myProject);
    params.setErrorNotificationTitle(GitBundle.message("update.process.generic.error.title"));
    params.setMergeDescription(GitBundle.message("update.process.error.message.unmerged.files"));
    return !new GitMergeCommittingConflictResolver(myProject, getRootsFromRepositories(myRepositories),
                                                   params, false).merge();
  }

  /**
   * Check if rebase is in progress, propose to resolve conflicts.
   * @return true if rebase is in progress, which means that update can't continue.
   */
  private boolean checkRebaseInProgress() {
    LOG.info("checkRebaseInProgress: checking if there is an unfinished rebase process...");
    Collection<VirtualFile> rebasingRoots = ContainerUtil.map(GitRebaseUtils.getRebasingRepositories(myProject), repo -> repo.getRoot());
    if (rebasingRoots.isEmpty()) {
      return false;
    }
    LOG.info("checkRebaseInProgress: roots with unfinished rebase: " + rebasingRoots);

    GitConflictResolver.Params params = new GitConflictResolver.Params(myProject);
    params.setErrorNotificationTitle(GitBundle.message("update.process.generic.error.title"));
    params.setMergeDescription(GitBundle.message("update.process.error.description.unfinished.rebase"));
    params.setErrorNotificationAdditionalDescription(GitBundle.message("update.process.error.additional.description.unfinished.rebase"));
    params.setReverse(true);
    return !new GitConflictResolver(myProject, rebasingRoots, params) {
      @Override
      protected boolean proceedIfNothingToMerge() {
        return new GitRebaser(myProject, myGit, myProgressIndicator).continueRebase(rebasingRoots);
      }

      @Override
      protected boolean proceedAfterAllMerged() {
        return new GitRebaser(myProject, myGit, myProgressIndicator).continueRebase(rebasingRoots);
      }
    }.merge();
  }
}
