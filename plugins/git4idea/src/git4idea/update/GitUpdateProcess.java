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
package git4idea.update;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.impl.LocalChangesUnderRoots;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitLocalBranch;
import git4idea.GitUtil;
import git4idea.branch.GitBranchPair;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.Git;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersionSpecialty;
import git4idea.config.UpdateMethod;
import git4idea.merge.GitConflictResolver;
import git4idea.merge.GitMergeCommittingConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.rebase.GitRebaser;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import git4idea.util.GitPreservingProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static git4idea.GitUtil.getRootsFromRepositories;
import static git4idea.GitUtil.mention;
import static git4idea.util.GitUIUtil.*;

/**
 * Handles update process (pull via merge or rebase) for several roots.
 *
 * @author Kirill Likhodedov
 */
public class GitUpdateProcess {
  private static final Logger LOG = Logger.getInstance(GitUpdateProcess.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final ChangeListManager myChangeListManager;

  @NotNull private final List<GitRepository> myRepositories;
  private final boolean myCheckRebaseOverMergeProblem;
  private final boolean myCheckForTrackedBranchExistance;
  private final UpdatedFiles myUpdatedFiles;
  @NotNull private final ProgressIndicator myProgressIndicator;
  @NotNull private final GitMerger myMerger;

  public GitUpdateProcess(@NotNull Project project,
                          @Nullable ProgressIndicator progressIndicator,
                          @NotNull Collection<GitRepository> repositories,
                          @NotNull UpdatedFiles updatedFiles,
                          boolean checkRebaseOverMergeProblem,
                          boolean checkForTrackedBranchExistance) {
    myProject = project;
    myCheckRebaseOverMergeProblem = checkRebaseOverMergeProblem;
    myCheckForTrackedBranchExistance = checkForTrackedBranchExistance;
    myGit = Git.getInstance();
    myChangeListManager = ChangeListManager.getInstance(project);
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myUpdatedFiles = updatedFiles;

    myRepositories = GitUtil.getRepositoryManager(project).sortByDependency(repositories);
    myProgressIndicator = progressIndicator == null ? new EmptyProgressIndicator() : progressIndicator;
    myMerger = new GitMerger(myProject);
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
  @NotNull
  public GitUpdateResult update(final UpdateMethod updateMethod) {
    LOG.info("update started|" + updateMethod);
    String oldText = myProgressIndicator.getText();
    myProgressIndicator.setText("Updating...");

    for (GitRepository repository : myRepositories) {
      repository.update();
    }

    // check if update is possible
    if (checkRebaseInProgress() || isMergeInProgress() || areUnmergedFiles()) {
      return GitUpdateResult.NOT_READY;
    }
    if (checkTrackedBranchesConfiguration() == null) {
      return GitUpdateResult.NOT_READY;
    }

    if (!fetchAndNotify()) {
      return GitUpdateResult.NOT_READY;
    }

    AccessToken token = DvcsUtil.workingTreeChangeStarted(myProject);
    GitUpdateResult result;
    try {
      result = updateImpl(updateMethod);
    }
    finally {
      token.finish();
    }
    myProgressIndicator.setText(oldText);
    return result;
  }

  @NotNull
  private GitUpdateResult updateImpl(@NotNull UpdateMethod updateMethod) {
    Map<VirtualFile, GitBranchPair> trackedBranches = checkTrackedBranchesConfiguration();
    if (trackedBranches == null) {
      return GitUpdateResult.NOT_READY;
    }

    Map<GitRepository, GitUpdater> updaters;
    try {
      updaters = defineUpdaters(updateMethod, trackedBranches);
    }
    catch (VcsException e) {
      LOG.info(e);
      notifyError(myProject, "Git update failed", e.getMessage(), true, e);
      return GitUpdateResult.ERROR;
    }

    if (updaters.isEmpty()) {
      return GitUpdateResult.NOTHING_TO_UPDATE;
    }

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
            VirtualFile root = repo.getRoot();
            GitBranchPair branchAndTracked = trackedBranches.get(root);
            if (branchAndTracked == null) {
              LOG.error("No tracked branch information for root " + root);
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
    final Collection<VirtualFile> myRootsToSave = ContainerUtil.newArrayList();
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
    new GitPreservingProcess(myProject, myGit, myRootsToSave, "Update", "Remote",
                             GitVcsSettings.getInstance(myProject).updateChangesPolicy(), myProgressIndicator, () -> {
                               LOG.info("updateImpl: updating...");
                               GitRepository currentlyUpdatedRoot = null;
                               try {
                                 for (GitRepository repo : myRepositories) {
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
                                 notifyImportantError(myProject, "Error updating " + rootName,
                                                      "Updating " + rootName + " failed with an error: " + e.getLocalizedMessage());
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

  @NotNull
  private Collection<GitRepository> findRootsRebasingOverMerge(@NotNull Map<GitRepository, GitUpdater> updaters) {
    return ContainerUtil.mapNotNull(updaters.keySet(), repo -> {
      GitUpdater updater = updaters.get(repo);
      if (updater instanceof GitRebaseUpdater) {
        String currentRef = updater.getSourceAndTarget().getBranch().getFullName();
        String baseRef = ObjectUtils.assertNotNull(updater.getSourceAndTarget().getDest()).getFullName();
        return GitRebaseOverMergeProblem.hasProblem(myProject, repo.getRoot(), baseRef, currentRef) ? repo : null;
      }
      return null;
    });
  }

  @NotNull
  private Map<GitRepository, GitUpdater> tryFastForwardMergeForRebaseUpdaters(@NotNull Map<GitRepository, GitUpdater> updaters) {
    Map<GitRepository, GitUpdater> modifiedUpdaters = new HashMap<>();
    Map<VirtualFile, Collection<Change>> changesUnderRoots =
      new LocalChangesUnderRoots(myChangeListManager, myVcsManager).getChangesUnderRoots(getRootsFromRepositories(updaters.keySet()));
    for (GitRepository repository : myRepositories) {
      GitUpdater updater = updaters.get(repository);
      if (updater == null) continue;
      Collection<Change> changes = changesUnderRoots.get(repository.getRoot());
      LOG.debug("Changes under root '" + getShortRepositoryName(repository) + "': " + changes);
      if (updater instanceof GitRebaseUpdater && changes != null && !changes.isEmpty()) {
        // check only if there are local changes, otherwise stash won't happen anyway and there would be no optimization
        GitRebaseUpdater rebaseUpdater = (GitRebaseUpdater) updater;
        if (rebaseUpdater.fastForwardMerge()) {
          continue;
        }
      }
      modifiedUpdaters.put(repository, updater);
    }
    return modifiedUpdaters;
  }

  @NotNull
  private Map<GitRepository, GitUpdater> defineUpdaters(@NotNull UpdateMethod updateMethod,
                                                        @NotNull Map<VirtualFile, GitBranchPair> trackedBranches) throws VcsException {
    final Map<GitRepository, GitUpdater> updaters = new HashMap<>();
    LOG.info("updateImpl: defining updaters...");
    for (GitRepository repository : myRepositories) {
      VirtualFile root = repository.getRoot();
      GitBranchPair branchAndTracked = trackedBranches.get(root);
      if (branchAndTracked == null) continue;
      GitUpdater updater = GitUpdater.getUpdater(myProject, myGit, branchAndTracked, repository, myProgressIndicator, myUpdatedFiles,
                                                 updateMethod);
      if (updater.isUpdateNeeded()) {
        updaters.put(repository, updater);
      }
      LOG.info("update| root=" + root + " ,updater=" + updater);
    }
    return updaters;
  }

  @NotNull
  private static GitUpdateResult joinResults(@Nullable GitUpdateResult compoundResult, GitUpdateResult result) {
    if (compoundResult == null) {
      return result;
    }
    return compoundResult.join(result);
  }

  // fetch all roots. If an error happens, return false and notify about errors.
  private boolean fetchAndNotify() {
    return new GitFetcher(myProject, myProgressIndicator, false).fetchRootsAndNotify(myRepositories, "Update failed", false);
  }

  /**
   * For each root check that the repository is on branch, and this branch is tracking a remote branch, and the remote branch exists.
   * If it is not true for at least one of roots, notify and return null.
   * If branch configuration is OK for all roots, return the collected tracking branch information.
   */
  @Nullable
  private Map<VirtualFile, GitBranchPair> checkTrackedBranchesConfiguration() {
    Map<VirtualFile, GitBranchPair> trackedBranches = ContainerUtil.newHashMap();
    LOG.info("checking tracked branch configuration...");
    for (GitRepository repository : myRepositories) {
      VirtualFile root = repository.getRoot();
      final GitLocalBranch branch = repository.getCurrentBranch();
      if (branch == null) {
        LOG.info("checkTrackedBranchesConfigured: current branch is null in " + repository);
        notifyImportantError(myProject, "Can't update: no current branch",
                             "You are in 'detached HEAD' state, which means that you're not on any branch" +
                             mention(repository) + "<br/>" +
                             "Checkout a branch to make update possible.");
        return null;
      }
      GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfoForBranch(repository, branch);
      if (trackInfo == null) {
        LOG.info(String.format("checkTrackedBranchesConfigured: no track info for current branch %s in %s", branch, repository));
        if (myCheckForTrackedBranchExistance) {
          notifyImportantError(repository.getProject(), "Can't Update", getNoTrackedBranchError(repository, branch.getName()));
          return null;
        }
      }
      else {
        trackedBranches.put(root, new GitBranchPair(branch, trackInfo.getRemoteBranch()));
      }
    }
    return trackedBranches;
  }

  @VisibleForTesting
  @NotNull
  static String getNoTrackedBranchError(@NotNull GitRepository repository, @NotNull String branchName) {
    String recommendedCommand = recommendSetupTrackingCommand(repository, branchName);
    return "No tracked branch configured for branch " + code(branchName) +
    mention(repository) +
    " or the branch doesn't exist.<br/>" +
    "To make your branch track a remote branch call, for example,<br/>" +
    "<code>" + recommendedCommand + "</code>";
  }

  @NotNull
  private static String recommendSetupTrackingCommand(@NotNull GitRepository repository, @NotNull String branchName) {
    return String.format(GitVersionSpecialty.KNOWS_SET_UPSTREAM_TO.existsIn(repository.getVcs().getVersion()) ?
                         "git branch --set-upstream-to=origin/%1$s %1$s" :
                         "git branch --set-upstream %1$s origin/%1$s", branchName);
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
    GitConflictResolver.Params params = new GitConflictResolver.Params();
    params.setErrorNotificationTitle("Can't update");
    params.setMergeDescription("You have unfinished merge. These conflicts must be resolved before update.");
    return !new GitMergeCommittingConflictResolver(myProject, myGit, myMerger, mergingRoots, params, false).merge();
  }

  /**
   * Checks if there are unmerged files (which may still be possible even if rebase or merge have finished)
   * @return true if there are unmerged files at
   */
  private boolean areUnmergedFiles() {
    LOG.info("areUnmergedFiles: checking if there are unmerged files...");
    GitConflictResolver.Params params = new GitConflictResolver.Params();
    params.setErrorNotificationTitle("Update was not started");
    params.setMergeDescription("Unmerged files detected. These conflicts must be resolved before update.");
    return !new GitMergeCommittingConflictResolver(myProject, myGit, myMerger, getRootsFromRepositories(myRepositories),
                                                   params, false).merge();
  }

  /**
   * Check if rebase is in progress, propose to resolve conflicts.
   * @return true if rebase is in progress, which means that update can't continue.
   */
  private boolean checkRebaseInProgress() {
    LOG.info("checkRebaseInProgress: checking if there is an unfinished rebase process...");
    final GitRebaser rebaser = new GitRebaser(myProject, myGit, myProgressIndicator);
    final Collection<VirtualFile> rebasingRoots = rebaser.getRebasingRoots();
    if (rebasingRoots.isEmpty()) {
      return false;
    }
    LOG.info("checkRebaseInProgress: roots with unfinished rebase: " + rebasingRoots);

    GitConflictResolver.Params params = new GitConflictResolver.Params();
    params.setErrorNotificationTitle("Can't update");
    params.setMergeDescription("You have unfinished rebase process. These conflicts must be resolved before update.");
    params.setErrorNotificationAdditionalDescription("Then you may <b>continue rebase</b>. <br/> You also may <b>abort rebase</b> to restore the original branch and stop rebasing.");
    params.setReverse(true);
    return !new GitConflictResolver(myProject, myGit, rebasingRoots, params) {
      @Override protected boolean proceedIfNothingToMerge() {
        return rebaser.continueRebase(rebasingRoots);
      }

      @Override protected boolean proceedAfterAllMerged() {
        return rebaser.continueRebase(rebasingRoots);
      }
    }.merge();
  }
}
