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
package git4idea.update;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.impl.LocalChangesUnderRoots;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.ContinuationFinalTasksInserter;
import com.intellij.util.continuation.TaskDescriptor;
import com.intellij.util.continuation.Where;
import com.intellij.util.text.DateFormatUtil;
import git4idea.GitLocalBranch;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.branch.GitBranchPair;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.Git;
import git4idea.merge.GitConflictResolver;
import git4idea.merge.GitMergeCommittingConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.rebase.GitRebaser;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRepository;
import git4idea.stash.GitChangesSaver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

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
  @NotNull private final Collection<GitRepository> myRepositories;
  private final UpdatedFiles myUpdatedFiles;
  private final ProgressIndicator myProgressIndicator;
  private final GitMerger myMerger;
  private final GitChangesSaver mySaver;

  private final Map<VirtualFile, GitBranchPair> myTrackedBranches = new HashMap<VirtualFile, GitBranchPair>();
  private GitUpdateResult myResult;
  private final Collection<VirtualFile> myRootsToSave;

  public enum UpdateMethod {
    MERGE,
    REBASE,
    READ_FROM_SETTINGS
  }

  public GitUpdateProcess(@NotNull Project project, @NotNull GitPlatformFacade platformFacade, @Nullable ProgressIndicator progressIndicator,
                          @NotNull Collection<GitRepository> repositories, @NotNull UpdatedFiles updatedFiles) {
    myProject = project;
    myRepositories = repositories;
    myGit = ServiceManager.getService(Git.class);
    myUpdatedFiles = updatedFiles;
    myProgressIndicator = progressIndicator == null ? new EmptyProgressIndicator() : progressIndicator;
    myMerger = new GitMerger(myProject);
    mySaver = GitChangesSaver.getSaver(myProject, platformFacade, myGit,
                                       myProgressIndicator,
                                       "Uncommitted changes before update operation at " + DateFormatUtil.formatDateTime(Clock.getTime()));
    myRootsToSave = new HashSet<VirtualFile>(1);
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
    if (checkRebaseInProgress() || isMergeInProgress() || areUnmergedFiles() || !checkTrackedBranchesConfigured()) {
      return GitUpdateResult.NOT_READY;
    }

    if (!fetchAndNotify()) {
      return GitUpdateResult.NOT_READY;
    }

    GitComplexProcess.Operation updateOperation = new GitComplexProcess.Operation() {
      @Override public void run(ContinuationContext continuationContext) {
        myResult = updateImpl(updateMethod, continuationContext);
      }
    };
    GitComplexProcess.execute(myProject, "update", updateOperation);

    myProgressIndicator.setText(oldText);
    return myResult;
  }

  @NotNull
  private GitUpdateResult updateImpl(@NotNull UpdateMethod updateMethod, ContinuationContext context) {
    Map<VirtualFile, GitUpdater> updaters;
    try {
      updaters = defineUpdaters(updateMethod);
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

    // save local changes if needed (update via merge may perform without saving).
    LOG.info("updateImpl: identifying if save is needed...");
    for (Map.Entry<VirtualFile, GitUpdater> entry : updaters.entrySet()) {
      VirtualFile root = entry.getKey();
      GitUpdater updater = entry.getValue();
      if (updater.isSaveNeeded()) {
        myRootsToSave.add(root);
        LOG.info("update| root " + root + " needs save");
      }
    }

    LOG.info("updateImpl: saving local changes...");
    try {
      mySaver.saveLocalChanges(myRootsToSave);
    } catch (VcsException e) {
      LOG.info("Couldn't save local changes", e);
      notifyError(myProject, "Git update failed",
                  "Tried to save uncommitted changes in " + mySaver.getSaverName() + " before update, but failed with an error.<br/>" +
                  "Update was cancelled.", true, e);
      return GitUpdateResult.ERROR;
    }

    // update each root
    LOG.info("updateImpl: updating...");
    boolean incomplete = false;
    GitUpdateResult compoundResult = null;
    VirtualFile currentlyUpdatedRoot = null;
    try {
      for (Map.Entry<VirtualFile, GitUpdater> entry : updaters.entrySet()) {
        currentlyUpdatedRoot = entry.getKey();
        GitUpdater updater = entry.getValue();
        GitUpdateResult res = updater.update();
        LOG.info("updating root " + currentlyUpdatedRoot + " finished: " + res);
        if (res == GitUpdateResult.INCOMPLETE) {
          incomplete = true;
        }
        compoundResult = joinResults(compoundResult, res);
      }
    } catch (VcsException e) {
      String rootName = (currentlyUpdatedRoot == null) ? "" : currentlyUpdatedRoot.getName();
      LOG.info("Error updating changes for root " + currentlyUpdatedRoot, e);
      notifyImportantError(myProject, "Error updating " + rootName,
                           "Updating " + rootName + " failed with an error: " + e.getLocalizedMessage());
    } finally {
      // Note: compoundResult normally should not be null, because the updaters map was checked for non-emptiness.
      // But if updater.update() fails with exception for the first root, then the value would not be assigned.
      // In this case we don't restore local changes either, because update failed.
      if (incomplete || compoundResult == null || !compoundResult.isSuccess()) {
        mySaver.notifyLocalChangesAreNotRestored();
      }
      else {
        LOG.info("updateImpl: restoring local changes...");
        restoreLocalChanges(context);
      }
    }
    return compoundResult;
  }

  @NotNull
  private Map<VirtualFile, GitUpdater> tryFastForwardMergeForRebaseUpdaters(@NotNull Map<VirtualFile, GitUpdater> updaters) {
    Map<VirtualFile, GitUpdater> modifiedUpdaters = new HashMap<VirtualFile, GitUpdater>();
    Map<VirtualFile, Collection<Change>> changesUnderRoots =
      new LocalChangesUnderRoots(ChangeListManager.getInstance(myProject), ProjectLevelVcsManager.getInstance(myProject)).
        getChangesUnderRoots(updaters.keySet());
    for (Map.Entry<VirtualFile, GitUpdater> updaterEntry : updaters.entrySet()) {
      VirtualFile root = updaterEntry.getKey();
      GitUpdater updater = updaterEntry.getValue();
      Collection<Change> changes = changesUnderRoots.get(root);
      if (updater instanceof GitRebaseUpdater && changes != null && !changes.isEmpty()) {
        // check only if there are local changes, otherwise stash won't happen anyway and there would be no optimization
        GitRebaseUpdater rebaseUpdater = (GitRebaseUpdater) updater;
        if (rebaseUpdater.fastForwardMerge()) {
          continue;
        }
      }
      modifiedUpdaters.put(root, updater);
    }
    return modifiedUpdaters;
  }

  @NotNull
  private Map<VirtualFile, GitUpdater> defineUpdaters(@NotNull UpdateMethod updateMethod) throws VcsException {
    final Map<VirtualFile, GitUpdater> updaters = new HashMap<VirtualFile, GitUpdater>();
    LOG.info("updateImpl: defining updaters...");
    for (GitRepository repository : myRepositories) {
      VirtualFile root = repository.getRoot();
      final GitUpdater updater;
      if (updateMethod == UpdateMethod.MERGE) {
        updater = new GitMergeUpdater(myProject, myGit, root, myTrackedBranches, myProgressIndicator, myUpdatedFiles);
      } else if (updateMethod == UpdateMethod.REBASE) {
        updater = new GitRebaseUpdater(myProject, myGit, root, myTrackedBranches, myProgressIndicator, myUpdatedFiles);
      } else {
        updater = GitUpdater.getUpdater(myProject, myGit, myTrackedBranches, root, myProgressIndicator, myUpdatedFiles);
      }

      if (updater.isUpdateNeeded()) {
        updaters.put(root, updater);
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

  private void restoreLocalChanges(ContinuationContext context) {
    context.addExceptionHandler(VcsException.class, new Consumer<VcsException>() {
      @Override
      public void consume(VcsException e) {
        LOG.info("Couldn't restore local changes after update", e);
        notifyImportantError(myProject, "Couldn't restore local changes after update",
                             "Restoring changes saved before update failed with an error.<br/>" + e.getLocalizedMessage());
      }
    });
    // try restore changes under all circumstances
    final ContinuationFinalTasksInserter finalTasksInserter = new ContinuationFinalTasksInserter(context);
    finalTasksInserter.allNextAreFinal();
    // !!!! this task is put NEXT, i.e. if unshelve/unstash will be done synchronously or scheduled on context,
    // it is unimportant -> files will be refreshed after
    context.next(new TaskDescriptor("Refresh local files", Where.POOLED) {
      @Override
      public void run(ContinuationContext context) {
        mySaver.refresh();
      }
    });
    mySaver.restoreLocalChanges(context);
    finalTasksInserter.removeFinalPropertyAdder();
  }

  // fetch all roots. If an error happens, return false and notify about errors.
  private boolean fetchAndNotify() {
    return new GitFetcher(myProject, myProgressIndicator, false).fetchRootsAndNotify(myRepositories, "Update failed", false);
  }

  /**
   * For each root check that the repository is on branch, and this branch is tracking a remote branch,
   * and the remote branch exists.
   * If it is not true for at least one of roots, notify and return false.
   * If branch configuration is OK for all roots, return true.
   */
  private boolean checkTrackedBranchesConfigured() {
    LOG.info("checking tracked branch configuration...");
    for (GitRepository repository : myRepositories) {
      VirtualFile root = repository.getRoot();
      final GitLocalBranch branch = repository.getCurrentBranch();
      if (branch == null) {
        LOG.info("checkTrackedBranchesConfigured: current branch is null in " + repository);
        notifyImportantError(myProject, "Can't update: no current branch",
                             "You are in 'detached HEAD' state, which means that you're not on any branch" +
                             rootStringIfNeeded(root) +
                             "Checkout a branch to make update possible.");
        return false;
      }
      GitBranchTrackInfo trackInfo = GitBranchUtil.getTrackInfoForBranch(repository, branch);
      if (trackInfo == null) {
        final String branchName = branch.getName();
        LOG.info(String.format("checkTrackedBranchesConfigured: no track info for current branch %s in %s", branch, repository));
        notifyImportantError(myProject, "Can't update: no tracked branch",
                             "No tracked branch configured for branch " + code(branchName) +
                             rootStringIfNeeded(root) +
                             "To make your branch track a remote branch call, for example,<br/>" +
                             "<code>git branch --set-upstream " + branchName + " origin/" + branchName + "</code>");
        return false;
      }
      myTrackedBranches.put(root, new GitBranchPair(branch, trackInfo.getRemoteBranch()));
    }
    return true;
  }

  private String rootStringIfNeeded(@NotNull VirtualFile root) {
    if (myRepositories.size() < 2) {
      return ".<br/>";
    }
    return "<br/>in Git repository " + code(root.getPresentableUrl()) + "<br/>";
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
    return !new GitMergeCommittingConflictResolver(myProject, myGit, myMerger, GitUtil.getRootsFromRepositories(myRepositories),
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
    return !new GitConflictResolver(myProject, myGit, ServiceManager.getService(GitPlatformFacade.class), rebasingRoots, params) {
      @Override protected boolean proceedIfNothingToMerge() {
        return rebaser.continueRebase(rebasingRoots);
      }

      @Override protected boolean proceedAfterAllMerged() {
        return rebaser.continueRebase(rebasingRoots);
      }
    }.merge();
  }
}
