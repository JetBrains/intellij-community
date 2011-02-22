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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.merge.GitMergeUtil;
import git4idea.rebase.GitRebaseProblemDetector;
import git4idea.rebase.GitRebaser;
import git4idea.ui.GitUIUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles 'git pull --rebase'
 */
public class GitRebaseUpdater extends GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitRebaseUpdater.class.getName());
  private final GitRebaser myRebaser;

  public GitRebaseUpdater(Project project, VirtualFile root, ProgressIndicator progressIndicator, UpdatedFiles updatedFiles) {
    super(project, root, progressIndicator, updatedFiles);
    myRebaser = new GitRebaser(myProject);
  }

  protected GitUpdateResult doUpdate() {
    final GitLineHandler pullHandler = makePullHandler(myRoot);
    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    pullHandler.addLineListener(rebaseConflictDetector);

    GitTask pullTask = new GitTask(myProject, pullHandler, "git pull");
    pullTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    final AtomicReference<GitUpdateResult> updateResult = new AtomicReference<GitUpdateResult>();
    pullTask.executeInBackground(true, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
        updateResult.set(GitUpdateResult.SUCCESS);
      }

      @Override protected void onCancel() {
        cancel();
        updateResult.set(GitUpdateResult.CANCEL);
      }

      @Override protected void onFailure() {
        if (rebaseConflictDetector.isMergeConflict()) {
          try {
            Collection<VirtualFile> unmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, myRoot);
            if (unmergedFiles.isEmpty()) {
              myRebaser.continueRebase(myRoot);
            } else {
              final Collection<VirtualFile> finalUnmergedFiles = unmergedFiles;
              UIUtil.invokeAndWaitIfNeeded(new Runnable() {
                @Override public void run() {
                  myVcsHelper.showMergeDialog(new ArrayList<VirtualFile>(finalUnmergedFiles), myVcs.getReverseMergeProvider());
                }
              });
              unmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, myRoot);
              if (unmergedFiles.isEmpty()) {
                myRebaser.continueRebase(myRoot);
              } else {
                updateResult.set(GitUpdateResult.INCOMPLETE);
                Notifications.Bus.notify(new Notification(GitVcs.IMPORTANT_ERROR_NOTIFICATION, "Can't continue rebase",
                                                          "You must resolve all conflicts first. <br/>" +
                                                          "Then you may continue or abort rebase.", NotificationType.WARNING), myProject);
              }
            }
          } catch (VcsException e) {
            updateResult.set(GitUpdateResult.INCOMPLETE);
            Notifications.Bus.notify(new Notification(GitVcs.IMPORTANT_ERROR_NOTIFICATION, "Can't continue rebase",
                                                      "Be sure to resolve all conflicts first. <br/>" +
                                                      "Then you may continue or abort rebase.<br/>" +
                                                      e.getLocalizedMessage(), NotificationType.WARNING), myProject);
          }
        } else {
          GitUIUtil.notifyImportantError(myProject, "Error rebasing", GitUIUtil.stringifyErrors(pullHandler.errors()));
          updateResult.set(GitUpdateResult.ERROR);
        }
      }
    });

    return updateResult.get();
  }

    // TODO
    //if (!checkLocallyModified(myRoot)) {
    //  cancel();
    //  updateSucceeded.set(false);
    //}


    // TODO: show at any case of update successfullibility, also don't show here but for all roots
    //if (mySkippedCommits.size() > 0) {
    //  GitSkippedCommits.showSkipped(myProject, mySkippedCommits);
    //}

  public void cancel() {
    myRebaser.abortRebase(myRoot);
    myProgressIndicator.setText2("Refreshing files for the root " + myRoot.getPath());
    myRoot.refresh(false, true);
  }

  protected GitLineHandler makePullHandler(VirtualFile root) {
    final GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.PULL);
    h.addParameters("--rebase");
    h.addParameters("--no-stat");
    h.addParameters("-v");
    return h;
  }

  /**
   * Check and process locally modified files
   *
   * @param root      the project root
   * @param ex        the exception holder
   */
  protected boolean checkLocallyModified(final VirtualFile root) throws VcsException {
    final Ref<Boolean> cancelled = new Ref<Boolean>(false);
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      public void run() {
        if (!GitUpdateLocallyModifiedDialog.showIfNeeded(myProject, root)) {
          cancelled.set(true);
        }
      }
    });
    return !cancelled.get();
  }
}
