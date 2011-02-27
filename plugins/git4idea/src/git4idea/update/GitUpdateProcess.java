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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Clock;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import git4idea.GitBranch;
import git4idea.GitVcs;
import git4idea.merge.GitMergeConflictResolver;
import git4idea.merge.GitMergeUtil;
import git4idea.merge.GitMerger;
import git4idea.rebase.GitRebaser;

import java.util.Collection;
import java.util.Set;

import static git4idea.ui.GitUIUtil.notifyError;
import static git4idea.ui.GitUIUtil.notifyImportantError;

/**
 * Handles update process (pull via merge or rebase) for several roots.
 *
 * @author Kirill Likhodedov
 */
public class GitUpdateProcess {
  private static final Logger LOG = Logger.getInstance(GitUpdateProcess.class);

  private final Project myProject;
  private final ProjectManagerEx myProjectManager;
  private final Set<VirtualFile> myRoots;
  private final UpdatedFiles myUpdatedFiles;
  private final ProgressIndicator myProgressIndicator;
  private final GitVcs myVcs;
  private final AbstractVcsHelper myVcsHelper;
  private final GitMerger myMerger;

  public GitUpdateProcess(Project project,
                          ProgressIndicator progressIndicator,
                          Set<VirtualFile> roots, UpdatedFiles updatedFiles) {
    myProject = project;
    myRoots = roots;
    myUpdatedFiles = updatedFiles;
    myProgressIndicator = progressIndicator;
    myProjectManager = ProjectManagerEx.getInstanceEx();
    myVcs = GitVcs.getInstance(myProject);
    myVcsHelper = AbstractVcsHelper.getInstance(project);
    myMerger = new GitMerger(myProject);
  }

  /**
   * Checks if update is possible, saves local changes and updates all roots.
   * In case of error shows notification and returns false. If update completes without errors, returns true.
   */
  public boolean update() {
    LOG.info("update started");
    final GitChangesSaver saver = GitChangesSaver.getSaver(myProject, myProgressIndicator,
      "Uncommitted changes before update operation at " + DateFormatUtil.formatDateTime(Clock.getTime()));

    myProjectManager.blockReloadingProjectOnExternalChanges();
    try {
      // check if update is possible
      if (checkRebaseInProgress() || checkMergeInProgress() || checkUnmergedFiles()) { return false; }
      if (!allTrackedBranchesConfigured()) { return false; }

      saver.saveLocalChanges();
      // update each root
      boolean incomplete = false;
      boolean success = true;
      for (final VirtualFile root : myRoots) {
        try {
          final GitUpdater updater = GitUpdater.getUpdater(myProject, root, myProgressIndicator, myUpdatedFiles);
          GitUpdateResult res = updater.update();
          if (res == GitUpdateResult.INCOMPLETE) {
            incomplete = true;
          }
          success &= res.isSuccess();
        } catch (VcsException e) {
          LOG.info("Error updating changes for root " + root, e);
          notifyImportantError(myProject, "Error updating " + root.getName(),
                               "Updating " + root.getName() + " failed with an error: " + e.getLocalizedMessage());
        } finally {
          try {
            if (!incomplete) {
              saver.restoreLocalChanges();
            } else {
              saver.notifyLocalChangesAreNotRestored();
            }
          } catch (VcsException e) {
            LOG.info("Couldn't restore local changes after update", e);
            notifyImportantError(myProject, "Couldn't restore local changes after update",
                                 "Restoring changes saved before update failed with an error.<br/>" + e.getLocalizedMessage());
          }
        }
      }
      return success;
    } catch (VcsException e) {
      LOG.info("Couldn't save local changes", e);
      notifyError(myProject, "Couldn't save local changes",
                  "Tried to save uncommitted changes in " + saver.getSaverName() + " before update, but failed with an error.<br/>" +
                  "Update was cancelled.", true, e);
    } finally {
      myProjectManager.unblockReloadingProjectOnExternalChanges();
    }
    return false;
  }

  /**
   * For each root check that the repository is on branch, and this branch is tracking a remote branch.
   * If it is not true for at least one of roots, notify and return false.
   * If branch configuration is OK for all roots, return true.
   */
  private boolean allTrackedBranchesConfigured() {
    for (VirtualFile root : myRoots) {
      try {
        final GitBranch branch = GitBranch.current(myProject, root);
        if (branch == null) {
          notifyImportantError(myProject, "Can't update: no current branch",
                               "You are in 'detached HEAD' state, which means that you're not on any branch.<br/>" +
                               "Checkout a branch to make update possible.");
          return false;
        }
        final String value = branch.getTrackedRemoteName(myProject, root);
        if (StringUtil.isEmpty(value)) {
          final String branchName = branch.getName();
          notifyImportantError(myProject, "Can't update: no tracked branch",
                               "No tracked branch configured for branch " + branchName +
                               "<br/>To make your branch track a remote branch call, for example,<br/>" +
                               "<code>git branch --set-upstream " + branchName + " origin/" + branchName + "</code>");
          return false;
        }
      } catch (VcsException e) {
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
  private boolean checkMergeInProgress() {
    final Collection<VirtualFile> mergingRoots = myMerger.getMergingRoots();
    if (mergingRoots.isEmpty()) {
      return false;
    }

    return !new GitMergeConflictResolver(myProject, false, "You have unfinished merge. These conflicts must be resolved before update.", "Can't update", "") {
      @Override protected boolean proceedAfterAllMerged() throws VcsException {
        myMerger.mergeCommit(mergingRoots);
        return true;
      }
    }.mergeFiles(mergingRoots);
  }

  /**
   * Checks if there are unmerged files (which may still be possible even if rebase or merge have finished)
   * @return true if there are unmerged files at
   */
  private boolean checkUnmergedFiles() {
    try {
      Collection<VirtualFile> unmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, myRoots);
      if (!unmergedFiles.isEmpty()) {
        return !new GitMergeConflictResolver(myProject, false, "Unmerged files detected. These conflicts must be resolved before update.", "Can't update", "") {
          @Override protected boolean proceedAfterAllMerged() throws VcsException {
            myMerger.mergeCommit(myRoots);
            return true;
          }
        }.mergeFiles(myRoots);
      }
    } catch (VcsException e) {
      LOG.info("areUnmergedFiles. Couldn't get unmerged files", e);
    }
    return false; // ignoring errors intentionally - if update will still be not possible, the user will be notified after. 
  }

  /**
   * Check if rebase is in progress, propose to resolve conflicts.
   * @return true if rebase is in progress, which means that update can't continue.
   */
  private boolean checkRebaseInProgress() {
    final GitRebaser rebaser = new GitRebaser(myProject);
    final Collection<VirtualFile> rebasingRoots = rebaser.getRebasingRoots();
    if (rebasingRoots.isEmpty()) {
      return false;
    }

    return !new GitMergeConflictResolver(myProject, true, "You have unfinished rebase process. These conflicts must be resolved before update.", "Can't update",
                                         "Then you may <b>continue rebase</b>. <br/> You also may <b>abort rebase</b> to restore the original branch and stop rebasing.") {
      @Override protected boolean proceedIfNothingToMerge() {
        return rebaser.continueRebase(rebasingRoots);
      }

      @Override protected boolean proceedAfterAllMerged() {
        return rebaser.continueRebase(rebasingRoots);
      }
    }.mergeFiles(rebasingRoots);
  }

}
