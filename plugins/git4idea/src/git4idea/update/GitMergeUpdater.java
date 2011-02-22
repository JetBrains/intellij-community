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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.merge.GitMergeConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.ui.GitUIUtil;

import java.util.Collections;
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
    final GitMerger merger = new GitMerger(myProject);
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

    final GitTask pullTask = new GitTask(myProject, pullHandler, "git pull");
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
          final boolean allMerged = new GitMergeConflictResolver(myProject, true, "Can't update", "") {
            @Override protected boolean proceedIfNothingToMerge() throws VcsException {
              merger.mergeCommit(myRoot);
              return true;
            }

            @Override protected boolean proceedAfterAllMerged() throws VcsException {
              merger.mergeCommit(myRoot);
              return true;
            }
          }.mergeFiles(Collections.singleton(myRoot));
          updateResult.set(allMerged ? GitUpdateResult.SUCCESS : GitUpdateResult.INCOMPLETE);
        } else {
          GitUIUtil.notifyImportantError(myProject, "Error merging", GitUIUtil.stringifyErrors(pullHandler.errors()));
          updateResult.set(GitUpdateResult.ERROR);
        }
      }
    });
    return updateResult.get();
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
