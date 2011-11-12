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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.branch.GitBranchPair;
import git4idea.commands.*;
import git4idea.merge.GitConflictResolver;
import git4idea.process.GitMessageWithFilesDetector;
import git4idea.rebase.GitRebaseProblemDetector;
import git4idea.rebase.GitRebaser;
import git4idea.ui.GitUIUtil;
import git4idea.util.UntrackedFilesNotifier;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handles 'git pull --rebase'
 */
public class GitRebaseUpdater extends GitUpdater {
  private static final Logger LOG = Logger.getInstance(GitRebaseUpdater.class.getName());
  private final GitRebaser myRebaser;

  public GitRebaseUpdater(Project project,
                          VirtualFile root,
                          final Map<VirtualFile, GitBranchPair> trackedBranches,
                          ProgressIndicator progressIndicator,
                          UpdatedFiles updatedFiles) {
    super(project, root, trackedBranches, progressIndicator, updatedFiles);
    myRebaser = new GitRebaser(myProject, myProgressIndicator);
  }

  @Override public boolean isSaveNeeded() {
    return true;
  }

  protected GitUpdateResult doUpdate() {
    LOG.info("doUpdate ");
    GitBranchPair gitBranchPair = myTrackedBranches.get(myRoot);
    String remoteBranch = gitBranchPair.getDest().getName();

    final GitLineHandler rebaseHandler = new GitLineHandler(myProject, myRoot, GitCommand.REBASE);
    rebaseHandler.addParameters(remoteBranch);
    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    rebaseHandler.addLineListener(rebaseConflictDetector);
    GitMessageWithFilesDetector untrackedWouldBeOverwrittenDetector = new GitMessageWithFilesDetector(GitMessageWithFilesDetector.Event.UNTRACKED_FILES_OVERWRITTEN_BY, myRoot);
    rebaseHandler.addLineListener(untrackedWouldBeOverwrittenDetector);

    GitTask rebaseTask = new GitTask(myProject, rebaseHandler, "Rebasing");
    rebaseTask.setProgressIndicator(myProgressIndicator);
    rebaseTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
    final AtomicReference<GitUpdateResult> updateResult = new AtomicReference<GitUpdateResult>();
    final AtomicBoolean failure = new AtomicBoolean();
    rebaseTask.executeInBackground(true, new GitTaskResultHandlerAdapter() {
      @Override
      protected void onSuccess() {
        updateResult.set(GitUpdateResult.SUCCESS);
      }

      @Override
      protected void onCancel() {
        cancel();
        updateResult.set(GitUpdateResult.CANCEL);
      }

      @Override
      protected void onFailure() {
        failure.set(true);
      }
    });

    if (failure.get()) {
      updateResult.set(handleRebaseFailure(rebaseHandler, rebaseConflictDetector, untrackedWouldBeOverwrittenDetector));
    }
    return updateResult.get();
  }

  private GitUpdateResult handleRebaseFailure(GitLineHandler pullHandler,
                                              GitRebaseProblemDetector rebaseConflictDetector,
                                              final GitMessageWithFilesDetector untrackedWouldBeOverwrittenDetector) {
    if (rebaseConflictDetector.isMergeConflict()) {
      LOG.info("handleRebaseFailure merge conflict");
      final boolean allMerged = new MyConflictResolver(myProject, myRoot, myRebaser).merge();
      return allMerged ? GitUpdateResult.SUCCESS : GitUpdateResult.INCOMPLETE;
    } else if (untrackedWouldBeOverwrittenDetector.wasMessageDetected()) {
      LOG.info("handleRebaseFailure: untracked files would be overwritten by checkout");
      UntrackedFilesNotifier.notifyUntrackedFilesOverwrittenBy(myProject, untrackedWouldBeOverwrittenDetector.getFiles(), "rebase");
      return GitUpdateResult.ERROR;
    } else {
      LOG.info("handleRebaseFailure error " + pullHandler.errors());
      GitUIUtil.notifyImportantError(myProject, "Rebase error", GitUIUtil.stringifyErrors(pullHandler.errors()));
      return GitUpdateResult.ERROR;
    }
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

  private static class MyConflictResolver extends GitConflictResolver {
    private final GitRebaser myRebaser;
    private final VirtualFile myRoot;

    public MyConflictResolver(Project project, VirtualFile root, GitRebaser rebaser) {
      super(project, Collections.singleton(root), makeParams());
      myRebaser = rebaser;
      myRoot = root;
    }
    
    private static Params makeParams() {
      Params params = new Params();
      params.setReverse(true);
      params.setMergeDescription("Merge conflicts detected. Resolve them before continuing rebase.");
      params.setErrorNotificationTitle("Can't continue rebase");
      params.setErrorNotificationAdditionalDescription("Then you may <b>continue rebase</b>. <br/> You also may <b>abort rebase</b> to restore the original branch and stop rebasing.");
      return params;
    }

    @Override protected boolean proceedIfNothingToMerge() throws VcsException {
      return myRebaser.continueRebase(myRoot);
    }

    @Override protected boolean proceedAfterAllMerged() throws VcsException {
      return myRebaser.continueRebase(myRoot);
    }
  }
}
