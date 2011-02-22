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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
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
import com.intellij.util.ui.UIUtil;
import git4idea.GitBranch;
import git4idea.GitVcs;
import git4idea.merge.GitMergeUtil;
import git4idea.rebase.GitRebaser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

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
  }

  /**
   * Checks if update is possible, saves local changes and updates all roots.
   * In case of error shows notification and returns false. If update completes without errors, returns true.
   */
  public boolean update() {
    LOG.info("update started");
    // check if update is possible
    if (isRebaseInProgressAndNotify() || isMergeInProgressAndNotify()) { return false; }
    if (!allTrackedBranchesConfigured()) { return false; }

    final GitChangesSaver saver = GitChangesSaver.getSaver(myProject, myProgressIndicator,
      "Uncommitted changes before update operation at " + DateFormatUtil.formatDateTime(Clock.getTime()));
    myProjectManager.blockReloadingProjectOnExternalChanges();
    try {
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
      notifyImportantError(myProject, "Couldn't save local changes", "Saving uncommitted changes before update failed with an error.<br/>" +
                                                                     "Update cancelled.<br/>" + e.getLocalizedMessage());
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

  private boolean isMergeInProgressAndNotify() {
    return false;
  }

  /**
   * Check if some roots are under the rebase operation and show a message in this case
   *
   * @return true if some roots are being rebased
   */
  private boolean isRebaseInProgressAndNotify() {
    final GitRebaser rebaser = new GitRebaser(myProject);
    final Collection<VirtualFile> rebasingRoots = rebaser.getRebasingRoots();
    if (rebasingRoots.isEmpty()) {
      return false;
    }

    try {
      Collection<VirtualFile> unmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, rebasingRoots);
      if (unmergedFiles.isEmpty()) {
        return rebaser.continueRebase(rebasingRoots);
      } else {
        // TODO add descriptive message to the dialog:
        // You must resolve all conflicts before you continue rebase
        final Collection<VirtualFile> finalUnmergedFiles = unmergedFiles;
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override public void run() {
            myVcsHelper.showMergeDialog(new ArrayList<VirtualFile>(finalUnmergedFiles), myVcs.getReverseMergeProvider());
          }
        });

        unmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, rebasingRoots);
        if (unmergedFiles.isEmpty()) {
          return rebaser.continueRebase(rebasingRoots);
        } else {
          // TODO link to "resolve all conflicts"
          Notifications.Bus.notify(new Notification(GitVcs.IMPORTANT_ERROR_NOTIFICATION, "Can't continue rebase",
                                                    "You must resolve all conflicts first. <br/>" +
                                                    "Then you may continue or abort rebase.", NotificationType.WARNING), myProject);
        }
      }
    } catch (VcsException e) {
      Notifications.Bus.notify(new Notification(GitVcs.IMPORTANT_ERROR_NOTIFICATION, "Can't continue rebase",
                                                "Be sure to resolve all conflicts first. <br/>" +
                                                "Then you may continue or abort rebase.<br/>" +
                                                e.getLocalizedMessage(), NotificationType.WARNING), myProject);
    }
    return true;
  }

}
