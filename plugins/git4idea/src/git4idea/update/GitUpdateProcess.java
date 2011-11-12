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

import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.continuation.ContinuationContext;
import com.intellij.util.continuation.ContinuationFinalTasksInserter;
import com.intellij.util.text.DateFormatUtil;
import git4idea.GitBranch;
import git4idea.GitVcs;
import git4idea.branch.GitBranchPair;
import git4idea.merge.GitConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.rebase.GitRebaser;
import git4idea.stash.GitChangesSaver;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static git4idea.ui.GitUIUtil.*;

/**
 * Handles update process (pull via merge or rebase) for several roots.
 *
 * @author Kirill Likhodedov
 */
public class GitUpdateProcess {
  private static final Logger LOG = Logger.getInstance(GitUpdateProcess.class);

  private final Project myProject;
  private final GitVcs myVcs;
  private final Set<VirtualFile> myRoots;
  private final UpdatedFiles myUpdatedFiles;
  private final ProgressIndicator myProgressIndicator;
  private final GitMerger myMerger;
  private final GitChangesSaver mySaver;

  private final Map<VirtualFile, GitBranchPair> myTrackedBranches = new HashMap<VirtualFile, GitBranchPair>();
  private boolean myResult;
  private final Map<VirtualFile, GitUpdater> myUpdaters;
  private final Collection<VirtualFile> myRootsToSave;
  
  public enum UpdateMethod {
    MERGE,
    REBASE,
    READ_FROM_SETTINGS
  }

  public GitUpdateProcess(@NotNull Project project,
                          @NotNull ProgressIndicator progressIndicator,
                          @NotNull Set<VirtualFile> roots, @NotNull UpdatedFiles updatedFiles) {
    myProject = project;
    myRoots = roots;
    myVcs = GitVcs.getInstance(project);
    myUpdatedFiles = updatedFiles;
    myProgressIndicator = progressIndicator;
    myMerger = new GitMerger(myProject);
    mySaver = GitChangesSaver.getSaver(myProject, myProgressIndicator,
      "Uncommitted changes before update operation at " + DateFormatUtil.formatDateTime(Clock.getTime()));
    myUpdaters = new HashMap<VirtualFile, GitUpdater>();
    myRootsToSave = new HashSet<VirtualFile>(1);
  }

  /**
   * Checks if update is possible, saves local changes and updates all roots.
   * In case of error shows notification and returns false. If update completes without errors, returns true.
   */
  public boolean update() {
    return update(UpdateMethod.READ_FROM_SETTINGS);
  }

  /**
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
  public boolean update(final UpdateMethod updateMethod) {
    LOG.info("update started|" + updateMethod);
    String oldText = myProgressIndicator.getText();
    myProgressIndicator.setText("Updating...");

    if (!fetchAndNotify()) {
      return false;
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

  private boolean updateImpl(UpdateMethod updateMethod, ContinuationContext context) {
    // define updaters for roots
    // check if update is possible
    if (checkRebaseInProgress() || isMergeInProgress() || areUnmergedFiles()) return false;
    if (!checkTrackedBranchesConfigured()) return false;

    try {
      for (VirtualFile root : myRoots) {
        final GitUpdater updater;
        if (updateMethod == UpdateMethod.MERGE) {
          updater = new GitMergeUpdater(myProject, root, myTrackedBranches, myProgressIndicator, myUpdatedFiles);
        } else if (updateMethod == UpdateMethod.REBASE) {
          updater = new GitRebaseUpdater(myProject, root, myTrackedBranches, myProgressIndicator, myUpdatedFiles);
        } else {
          updater = GitUpdater.getUpdater(myProject, myTrackedBranches, root, myProgressIndicator, myUpdatedFiles);
        }

        if (updater.isUpdateNeeded()) {
          myUpdaters.put(root, updater);
        }
        LOG.info("update| root=" + root + " ,updater=" + updater);
      }
    } catch (VcsException e) {
      LOG.info(e);
      notifyError(myProject, "Git update failed", e.getMessage(), true, e);
      return false;
    }

    if (myUpdaters.isEmpty()) return true;
    // save local changes if needed (update via merge may perform without saving).
    for (Map.Entry<VirtualFile, GitUpdater> entry : myUpdaters.entrySet()) {
      VirtualFile root = entry.getKey();
      GitUpdater updater = entry.getValue();
      if (updater.isSaveNeeded()) {
        myRootsToSave.add(root);
        LOG.info("update| root " + root + " needs save");
      }
    }

    try {
      mySaver.saveLocalChanges(myRootsToSave);
    } catch (VcsException e) {
      LOG.info("Couldn't save local changes", e);
      notifyError(myProject, "Git update failed",
                  "Tried to save uncommitted changes in " + mySaver.getSaverName() + " before update, but failed with an error.<br/>" +
                  "Update was cancelled.", true, e);
      return false;
    }

    // update each root
    boolean incomplete = false;
    boolean success = true;
    VirtualFile currentlyUpdatedRoot = null;
    try {
      for (Map.Entry<VirtualFile, GitUpdater> entry : myUpdaters.entrySet()) {
        currentlyUpdatedRoot = entry.getKey();
        GitUpdater updater = entry.getValue();
        GitUpdateResult res = updater.update();
        LOG.info("updating root " + currentlyUpdatedRoot + " finished: " + res);
        if (res == GitUpdateResult.INCOMPLETE) {
          incomplete = true;
        }
        success &= res.isSuccess();
      }
    } catch (VcsException e) {
      String rootName = (currentlyUpdatedRoot == null) ? "" : currentlyUpdatedRoot.getName();
      LOG.info("Error updating changes for root " + currentlyUpdatedRoot, e);
      notifyImportantError(myProject, "Error updating " + rootName,
                           "Updating " + rootName + " failed with an error: " + e.getLocalizedMessage());
    } finally {
      if (!incomplete) {
        restoreLocalChanges(context);
      } else {
        mySaver.notifyLocalChangesAreNotRestored();
      }
    }
    return success;
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
    mySaver.restoreLocalChanges(context);
    finalTasksInserter.removeFinalPropertyAdder();
  }

  // fetch all roots. If an error happens, return false and notify about errors.
  private boolean fetchAndNotify() {
    GitFetcher fetcher = new GitFetcher(myProject, myProgressIndicator);
    for (VirtualFile root : myRoots) {
      fetcher.fetch(root);
    }
    if (!fetcher.isSuccess()) {
      if (myVcs.getExecutableValidator().isExecutableValid()) {
        GitUIUtil.notifyMessage(myProject, "Update failed", "Couldn't fetch", NotificationType.ERROR, true, fetcher.getErrors());
      }
      return false;
    }
    return true;
  }

  public Map<VirtualFile, GitBranchPair> getTrackedBranches() {
    return myTrackedBranches;
  }

  public GitChangesSaver getSaver() {
    return mySaver;
  }

  /**
   * For each root check that the repository is on branch, and this branch is tracking a remote branch,
   * and the remote branch exists.
   * If it is not true for at least one of roots, notify and return false.
   * If branch configuration is OK for all roots, return true.
   */
  private boolean checkTrackedBranchesConfigured() {
    for (VirtualFile root : myRoots) {
      try {
        final GitBranch branch = GitBranch.current(myProject, root);
        if (branch == null) {
          LOG.info("checkTrackedBranchesConfigured current branch is null");
          notifyImportantError(myProject, "Can't update: no current branch",
                               "You are in 'detached HEAD' state, which means that you're not on any branch.<br/>" +
                               "Checkout a branch to make update possible.");
          return false;
        }
        final GitBranch tracked = branch.tracked(myProject, root);
        if (tracked == null) {
          final String branchName = branch.getName();
          LOG.info("checkTrackedBranchesConfigured tracked branch is null for current branch " + branch);
          notifyImportantError(myProject, "Can't update: no tracked branch",
                               "No tracked branch configured for branch " + branchName +
                               "<br/>To make your branch track a remote branch call, for example,<br/>" +
                               "<code>git branch --set-upstream " + branchName + " origin/" + branchName + "</code>");
          return false;
        }
        if (!tracked.exists(root)) {
          LOG.info("checkTrackedBranchesConfigured tracked branch " + tracked + "  doesn't exist.");
          notifyMessage(myProject, "Can't update: tracked branch doesn't exist.",
                        "Tracked branch <code>" + tracked.getName() + "</code> doesn't exist, so there is nothing to update.<br/>" +
                        "The branch will be automatically created when you push to it.",
                        NotificationType.WARNING, true, null);
          return false;
        }
        myTrackedBranches.put(root, new GitBranchPair(branch, tracked));
      } catch (VcsException e) {
        LOG.info("checkTrackedBranchesConfigured ", e);
        notifyImportantError(myProject, "Can't update: error identifying tracked branch", e.getLocalizedMessage());
        return false;
      }
    }
    return true;
  }

  /**
   * Check if merge is in progress, propose to resolve conflicts.
   * @return true if merge is in progress, which means that update can't continue.
   */
  private boolean isMergeInProgress() {
    final Collection<VirtualFile> mergingRoots = myMerger.getMergingRoots();
    if (mergingRoots.isEmpty()) {
      return false;
    }
    GitConflictResolver.Params params = new GitConflictResolver.Params();
    params.setErrorNotificationTitle("Can't update");
    params.setMergeDescription("You have unfinished merge. These conflicts must be resolved before update.");
    return !new MergeCommittingConflictResolver(myProject, myMerger, mergingRoots, params).merge();
  }

  /**
   * Checks if there are unmerged files (which may still be possible even if rebase or merge have finished)
   * @return true if there are unmerged files at
   */
  private boolean areUnmergedFiles() {
    GitConflictResolver.Params params = new GitConflictResolver.Params();
    params.setErrorNotificationTitle("Can't update");
    params.setMergeDescription("Unmerged files detected. These conflicts must be resolved before update.");
    return !new MergeCommittingConflictResolver(myProject, myMerger, myRoots, params).merge();
  }

  /**
   * Check if rebase is in progress, propose to resolve conflicts.
   * @return true if rebase is in progress, which means that update can't continue.
   */
  private boolean checkRebaseInProgress() {
    final GitRebaser rebaser = new GitRebaser(myProject, myProgressIndicator);
    final Collection<VirtualFile> rebasingRoots = rebaser.getRebasingRoots();
    if (rebasingRoots.isEmpty()) {
      return false;
    }
    LOG.info("checkRebaseInProgress rebasingRoots: " + rebasingRoots);

    GitConflictResolver.Params params = new GitConflictResolver.Params();
    params.setErrorNotificationTitle("Can't update");
    params.setMergeDescription("You have unfinished rebase process. These conflicts must be resolved before update.");
    params.setErrorNotificationAdditionalDescription("Then you may <b>continue rebase</b>. <br/> You also may <b>abort rebase</b> to restore the original branch and stop rebasing.");
    params.setReverse(true);
    return !new GitConflictResolver(myProject, rebasingRoots, params) {
      @Override protected boolean proceedIfNothingToMerge() {
        return rebaser.continueRebase(rebasingRoots);
      }

      @Override protected boolean proceedAfterAllMerged() {
        return rebaser.continueRebase(rebasingRoots);
      }
    }.merge();
  }

  // conflict resolver that makes a merge commit after all conflicts are resolved
  private static class MergeCommittingConflictResolver extends GitConflictResolver {
    private final Collection<VirtualFile> myMergingRoots;
    private final GitMerger myMerger;

    public MergeCommittingConflictResolver(Project project, GitMerger merger, Collection<VirtualFile> mergingRoots, Params params) {
      super(project, mergingRoots, params);
      myMerger = merger;
      myMergingRoots = mergingRoots;
    }

    @Override protected boolean proceedAfterAllMerged() throws VcsException {
      myMerger.mergeCommit(myMergingRoots);
      return true;
    }
  }
  
}
