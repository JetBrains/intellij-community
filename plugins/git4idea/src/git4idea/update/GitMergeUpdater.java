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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitBranch;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.merge.GitMergeUtil;
import git4idea.ui.GitUIUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles <code>git pull</code> via merge.
 */
public class GitMergeUpdater extends GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitMergeUpdater.class);

  private GitVcs myVcs;

  private enum MergeError {
    CONFLICT,
    LOCAL_CHANGES,
    OTHER
  }

  public GitMergeUpdater(Project project, VirtualFile root, ProgressIndicator progressIndicator, UpdatedFiles updatedFiles) {
    super(project, root, progressIndicator, updatedFiles);
    myVcs = GitVcs.getInstance(project);
  }

  @Override
  protected GitUpdateResult doUpdate() {
    final GitLineHandler pullHandler = makePullHandler(myRoot);
    final AtomicReference<MergeError> mergeError = new AtomicReference<MergeError>(MergeError.OTHER);
    pullHandler.addLineListener(new GitLineHandlerAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (line.contains("Automatic merge failed; fix conflicts and then commit the result")) {
          mergeError.set(MergeError.CONFLICT);
        } else if (line.contains("Please, commit your changes or stash them before you can merge")) {
          mergeError.set(MergeError.LOCAL_CHANGES);
        }
      }
    });

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
        final MergeError error = mergeError.get();
        if (error == MergeError.CONFLICT) {
          try {
            Collection<VirtualFile> unmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, myRoot);
            if (unmergedFiles.isEmpty()) {
              mergeCommit();
              updateResult.set(GitUpdateResult.SUCCESS);
            } else {
              final Collection<VirtualFile> finalUnmergedFiles = unmergedFiles;
              UIUtil.invokeAndWaitIfNeeded(new Runnable() {
                public void run() {
                  myVcsHelper.showMergeDialog(new ArrayList<VirtualFile>(finalUnmergedFiles), myVcs.getReverseMergeProvider());
                }
              });
              unmergedFiles = GitMergeUtil.getUnmergedFiles(myProject, myRoot);
              if (unmergedFiles.isEmpty()) {
                mergeCommit();
                updateResult.set(GitUpdateResult.SUCCESS);
              } else {
                updateResult.set(GitUpdateResult.INCOMPLETE);
                Notifications.Bus.notify(new Notification(GitVcs.IMPORTANT_ERROR_NOTIFICATION, "Can't continue rebase",
                                                          "You must resolve all conflicts first. <br/>" +
                                                          "Then you may continue or abort rebase.", NotificationType.WARNING),
                                         myProject);
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
          GitUIUtil.notifyImportantError(myProject, "Error merging", GitUIUtil.stringifyErrors(pullHandler.errors()));
          updateResult.set(GitUpdateResult.ERROR);
        }
      }
    });
    return updateResult.get();
  }

  private void mergeCommit() throws VcsException {
    GitSimpleHandler handler = new GitSimpleHandler(myProject, myRoot, GitCommand.COMMIT);
    handler.setNoSSH(true);

    File gitDir = new File(VfsUtil.virtualToIoFile(myRoot), ".git");
    File messageFile = new File(gitDir, "MERGE_MSG");
    if (!messageFile.exists()) {
      final GitBranch branch = GitBranch.current(myProject, myRoot);
      final String branchName = branch != null ? branch.getName() : "";
      handler.addParameters("-m", "Merge branch '" + branchName + "' of " + myRoot.getPresentableUrl() + "\n");
    } else {
      handler.addParameters("-F", messageFile.getAbsolutePath());
    }
    handler.endOptions();
    handler.run();
  }

  private void cancel() {
    try {
      GitSimpleHandler h = new GitSimpleHandler(myProject, myRoot, GitCommand.MERGE);
      h.addParameters("-v", "--abort");
      h.run();
    } catch (VcsException e) {
      LOG.info("cancel git merge --abort", e);
      GitUIUtil.notifyImportantError(myProject, "Couldn't abort merge", e.getLocalizedMessage());
    }
  }

  protected GitLineHandler makePullHandler(VirtualFile root) {
    GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.PULL);
    h.addParameters("--no-rebase");
    h.addParameters("--no-stat");
    h.addParameters("-v");
    return h;
  }
}
