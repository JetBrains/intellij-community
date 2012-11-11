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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import git4idea.GitBranch;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.Notificator;
import git4idea.branch.GitBranchPair;
import git4idea.commands.*;
import git4idea.merge.GitConflictResolver;
import git4idea.rebase.GitRebaseProblemDetector;
import git4idea.rebase.GitRebaser;
import git4idea.repo.GitRepository;
import git4idea.util.GitUIUtil;
import git4idea.util.UntrackedFilesNotifier;
import org.jetbrains.annotations.NotNull;

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

  public GitRebaseUpdater(@NotNull Project project, @NotNull Git git, @NotNull VirtualFile root,
                          @NotNull final Map<VirtualFile, GitBranchPair> trackedBranches,
                          ProgressIndicator progressIndicator,
                          UpdatedFiles updatedFiles) {
    super(project, git, root, trackedBranches, progressIndicator, updatedFiles);
    myRebaser = new GitRebaser(myProject, git, myProgressIndicator);
  }

  @Override public boolean isSaveNeeded() {
    return true;
  }

  protected GitUpdateResult doUpdate() {
    LOG.info("doUpdate ");
    String remoteBranch = getRemoteBranchToMerge();

    final GitLineHandler rebaseHandler = new GitLineHandler(myProject, myRoot, GitCommand.REBASE);
    rebaseHandler.addParameters(remoteBranch);
    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    rebaseHandler.addLineListener(rebaseConflictDetector);
    GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector = new GitUntrackedFilesOverwrittenByOperationDetector(myRoot);
    rebaseHandler.addLineListener(untrackedFilesDetector);

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
      updateResult.set(handleRebaseFailure(rebaseHandler, rebaseConflictDetector, untrackedFilesDetector));
    }
    return updateResult.get();
  }

  @NotNull
  private String getRemoteBranchToMerge() {
    GitBranchPair gitBranchPair = myTrackedBranches.get(myRoot);
    GitBranch dest = gitBranchPair.getDest();
    LOG.assertTrue(dest != null, String.format("Destination branch is null for source branch %s in %s",
                                               gitBranchPair.getSource().getName(), myRoot));
    return dest.getName();
  }

  private GitUpdateResult handleRebaseFailure(GitLineHandler pullHandler,
                                              GitRebaseProblemDetector rebaseConflictDetector,
                                              final GitMessageWithFilesDetector untrackedWouldBeOverwrittenDetector) {
    if (rebaseConflictDetector.isMergeConflict()) {
      LOG.info("handleRebaseFailure merge conflict");
      final boolean allMerged = new MyConflictResolver(myProject, myGit, myRoot, myRebaser).merge();
      return allMerged ? GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS : GitUpdateResult.INCOMPLETE;
    } else if (untrackedWouldBeOverwrittenDetector.wasMessageDetected()) {
      LOG.info("handleRebaseFailure: untracked files would be overwritten by checkout");
      UntrackedFilesNotifier.notifyUntrackedFilesOverwrittenBy(myProject, ServiceManager.getService(myProject, GitPlatformFacade.class),
                                                               untrackedWouldBeOverwrittenDetector.getFiles(), "rebase", null);
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

  @Override
  public String toString() {
    return "Rebase updater";
  }

  /**
   * Tries to execute {@code git merge --ff-only}.
   * @return true, if everything is successful; false for any error (to let a usual "fair" update deal with it).
   */
  public boolean fastForwardMerge() {
    LOG.info("Trying fast-forward merge for " + myRoot);
    GitRepository repository = GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(myRoot);
    if (repository == null) {
      LOG.error("Repository is null for " + myRoot);
      return false;
    }
    try {
      markStart(myRoot);
    }
    catch (VcsException e) {
      LOG.info("Couldn't mark start for repository " + myRoot, e);
      return false;
    }

    GitCommandResult result = myGit.merge(repository, getRemoteBranchToMerge(), Collections.singletonList("--ff-only"));

    try {
      markEnd(myRoot);
    }
    catch (VcsException e) {
      // this is not critical, and update has already happened,
      // so we just notify the user about problems with collecting the updated changes.
      LOG.info("Couldn't mark end for repository " + myRoot, e);
      Notificator.getInstance(myProject).
        notifyWeakWarning("Couldn't collect the updated files info",
                          String.format("Update of %s was successful, but we couldn't collect the updated changes because of an error",
                                        myRoot), null);
    }
    return result.success();
  }

  private static class MyConflictResolver extends GitConflictResolver {
    private final GitRebaser myRebaser;
    private final VirtualFile myRoot;

    public MyConflictResolver(Project project, @NotNull Git git, VirtualFile root, GitRebaser rebaser) {
      super(project, git, ServiceManager.getService(GitPlatformFacade.class), Collections.singleton(root), makeParams());
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
