// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.merge.GitConflictResolver;
import git4idea.update.GitUpdateResult;
import git4idea.util.GitUIUtil;
import git4idea.util.GitUntrackedFilesHelper;
import git4idea.util.LocalChangesWouldBeOverwrittenHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.CHECKOUT;

public class GitRebaser {

  private static final Logger LOG = Logger.getInstance(GitRebaser.class);

  @NotNull private final Project myProject;
  @NotNull private final Git myGit;
  @NotNull private final GitVcs myVcs;
  @NotNull private final ProgressIndicator myProgressIndicator;

  @NotNull private final List<GitRebaseUtils.CommitInfo> mySkippedCommits;

  public GitRebaser(@NotNull Project project, @NotNull Git git, @NotNull ProgressIndicator progressIndicator) {
    myProject = project;
    myGit = git;
    myProgressIndicator = progressIndicator;
    myVcs = GitVcs.getInstance(project);
    mySkippedCommits = new ArrayList<>();
  }

  public GitUpdateResult rebase(@NotNull VirtualFile root,
                                @NotNull List<String> parameters,
                                @Nullable final Runnable onCancel,
                                @Nullable GitLineHandlerListener lineListener) {
    final GitLineHandler rebaseHandler = createHandler(root);
    rebaseHandler.setStdoutSuppressed(false);
    rebaseHandler.addParameters(parameters);
    if (lineListener != null) {
      rebaseHandler.addLineListener(lineListener);
    }

    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    rebaseHandler.addLineListener(rebaseConflictDetector);
    GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector = new GitUntrackedFilesOverwrittenByOperationDetector(root);
    GitLocalChangesWouldBeOverwrittenDetector localChangesDetector = new GitLocalChangesWouldBeOverwrittenDetector(root, CHECKOUT);
    rebaseHandler.addLineListener(untrackedFilesDetector);
    rebaseHandler.addLineListener(localChangesDetector);
    rebaseHandler.addLineListener(GitStandardProgressAnalyzer.createListener(myProgressIndicator));

    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, "Rebase")) {
      String oldText = myProgressIndicator.getText();
      myProgressIndicator.setText("Rebasing...");
      GitCommandResult result = myGit.runCommand(rebaseHandler);
      myProgressIndicator.setText(oldText);
      return result.success() ?
             GitUpdateResult.SUCCESS :
             handleRebaseFailure(rebaseHandler, root, result, rebaseConflictDetector, untrackedFilesDetector, localChangesDetector);
    }
    catch (ProcessCanceledException pce) {
      if (onCancel != null) {
        onCancel.run();
      }
      return GitUpdateResult.CANCEL;
    }
  }

  protected GitLineHandler createHandler(VirtualFile root) {
    return new GitLineHandler(myProject, root, GitCommand.REBASE);
  }

  public void abortRebase(@NotNull VirtualFile root) {
    LOG.info("abortRebase " + root);
    final GitLineHandler rh = new GitLineHandler(myProject, root, GitCommand.REBASE);
    rh.setStdoutSuppressed(false);
    rh.addParameters("--abort");
    GitTask task = new GitTask(myProject, rh, "Aborting rebase");
    task.setProgressIndicator(myProgressIndicator);
    task.executeAsync(new GitTaskResultNotificationHandler(myProject, "Rebase aborted", "Abort rebase cancelled", "Error aborting rebase"));
  }

  public boolean continueRebase(@NotNull VirtualFile root) {
    return continueRebase(root, "--continue");
  }

  /**
   * Runs 'git rebase --continue' on several roots consequently.
   * @return true if rebase successfully finished.
   */
  public boolean continueRebase(@NotNull Collection<? extends VirtualFile> rebasingRoots) {
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, "Rebase")) {
      boolean success = true;
      for (VirtualFile root : rebasingRoots) {
        success &= continueRebase(root);
      }
      return success;
    }
  }

  // start operation may be "--continue" or "--skip" depending on the situation.
  private boolean continueRebase(final @NotNull VirtualFile root, @NotNull String startOperation) {
    LOG.info("continueRebase " + root + " " + startOperation);
    final GitLineHandler rh = new GitLineHandler(myProject, root, GitCommand.REBASE);
    rh.setStdoutSuppressed(false);
    rh.addParameters(startOperation);
    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    rh.addLineListener(rebaseConflictDetector);

    // TODO If interactive rebase with commit rewording was invoked, this should take the reworded message
    GitRebaser.TrivialEditor editor = new GitRebaser.TrivialEditor(myProject, root);
    try (GitHandlerRebaseEditorManager ignored = GitHandlerRebaseEditorManager.prepareEditor(rh, editor)) {
      final GitTask rebaseTask = new GitTask(myProject, rh, "git rebase " + startOperation);
      rebaseTask.setProgressAnalyzer(new GitStandardProgressAnalyzer());
      rebaseTask.setProgressIndicator(myProgressIndicator);
      return executeRebaseTaskInBackground(root, rh, rebaseConflictDetector, rebaseTask);
    }
  }

  /**
   * @return Roots which have unfinished rebase process. May be empty.
   */
  public @NotNull Collection<VirtualFile> getRebasingRoots() {
    final Collection<VirtualFile> rebasingRoots = new HashSet<>();
    for (VirtualFile root : ProjectLevelVcsManager.getInstance(myProject).getRootsUnderVcs(myVcs)) {
      if (GitRebaseUtils.isRebaseInTheProgress(myProject, root)) {
        rebasingRoots.add(root);
      }
    }
    return rebasingRoots;
  }

  private boolean executeRebaseTaskInBackground(VirtualFile root, GitLineHandler h, GitRebaseProblemDetector rebaseConflictDetector, GitTask rebaseTask) {
    final AtomicBoolean result = new AtomicBoolean();
    final AtomicBoolean failure = new AtomicBoolean();
    rebaseTask.executeInBackground(true, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
        result.set(true);
      }

      @Override protected void onCancel() {
        result.set(false);
      }

      @Override protected void onFailure() {
        failure.set(true);
      }
    });
    if (failure.get()) {
      result.set(handleRebaseFailure(root, h, rebaseConflictDetector));
    }
    return result.get();
  }

  /**
   * @return true if the failure situation was resolved successfully, false if we failed to resolve the problem.
   */
  private boolean handleRebaseFailure(final VirtualFile root, final GitLineHandler h, GitRebaseProblemDetector rebaseConflictDetector) {
    if (rebaseConflictDetector.isMergeConflict()) {
      LOG.info("handleRebaseFailure merge conflict");
      return new GitConflictResolver(myProject, Collections.singleton(root), makeParamsForRebaseConflict()) {
        @Override protected boolean proceedIfNothingToMerge() {
          return continueRebase(root, "--continue");
        }

        @Override protected boolean proceedAfterAllMerged() {
          return continueRebase(root, "--continue");
        }
      }.merge();
    }
    else if (rebaseConflictDetector.isNoChangeError()) {
      LOG.info("handleRebaseFailure no changes error detected");
      try {
        if (GitUtil.hasLocalChanges(true, myProject, root)) {
          LOG.error("The rebase detector incorrectly detected 'no changes' situation. Attempting to continue rebase.");
          return continueRebase(root);
        }
        else if (GitUtil.hasLocalChanges(false, myProject, root)) {
          LOG.warn("No changes from patch were not added to the index. Adding all changes from tracked files.");
          stageEverything(root);
          return continueRebase(root);
        }
        else {
          GitRebaseUtils.CommitInfo commit = GitRebaseUtils.getCurrentRebaseCommit(myProject, root);
          LOG.info("no changes confirmed. Skipping commit " + commit);
          mySkippedCommits.add(commit);
          return continueRebase(root, "--skip");
        }
      }
      catch (VcsException e) {
        LOG.info("Failed to work around 'no changes' error.", e);
        String message = "Couldn't proceed with rebase. " + e.getMessage();
        GitUIUtil.notifyImportantError(myProject, "Error rebasing", message);
        return false;
      }
    }
    else {
      LOG.info("handleRebaseFailure error " + h.errors());
      GitUIUtil.notifyImportantError(myProject, "Error rebasing", GitUIUtil.stringifyErrors(h.errors()));
      return false;
    }
  }

  private void stageEverything(@NotNull VirtualFile root) throws VcsException {
    GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.ADD);
    handler.setSilent(false);
    handler.addParameters("--update");
    myGit.runCommand(handler).throwOnError();
  }

  private GitConflictResolver.Params makeParamsForRebaseConflict() {
    return new GitConflictResolver.Params(myProject).
      setReverse(true).
      setErrorNotificationTitle("Can't continue rebase").
      setMergeDescription("Merge conflicts detected. Resolve them before continuing rebase.").
      setErrorNotificationAdditionalDescription("Then you may <b>continue rebase</b>. <br/> " +
                                                "You also may <b>abort rebase</b> to restore the original branch and stop rebasing.");
  }

  public static class TrivialEditor extends GitInteractiveRebaseEditorHandler{
    public TrivialEditor(@NotNull Project project, @NotNull VirtualFile root) {
      super(project, root);
    }

    @Override
    public int editCommits(@NotNull String path) {
      return 0;
    }
  }

  @NotNull
  public GitUpdateResult handleRebaseFailure(@NotNull GitLineHandler handler,
                                             @NotNull VirtualFile root,
                                             @NotNull GitCommandResult result,
                                             @NotNull GitRebaseProblemDetector rebaseConflictDetector,
                                             @NotNull GitMessageWithFilesDetector untrackedWouldBeOverwrittenDetector,
                                             @NotNull GitLocalChangesWouldBeOverwrittenDetector localChangesDetector) {
    if (rebaseConflictDetector.isMergeConflict()) {
      LOG.info("handleRebaseFailure merge conflict");
      final boolean allMerged = new GitRebaser.ConflictResolver(myProject, myGit, root, this).merge();
      return allMerged ? GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS : GitUpdateResult.INCOMPLETE;
    }
    else if (untrackedWouldBeOverwrittenDetector.wasMessageDetected()) {
      LOG.info("handleRebaseFailure: untracked files would be overwritten by checkout");
      GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(myProject, root,
                                                                untrackedWouldBeOverwrittenDetector.getRelativeFilePaths(), "rebase", null);
      return GitUpdateResult.ERROR;
    }
    else if (localChangesDetector.wasMessageDetected()) {
      LocalChangesWouldBeOverwrittenHelper.showErrorNotification(myProject, root, "rebase", localChangesDetector.getRelativeFilePaths());
      return GitUpdateResult.ERROR;
    }
    else {
      LOG.info("handleRebaseFailure error " + handler.errors());
      VcsNotifier.getInstance(myProject).notifyError("Rebase error", result.getErrorOutputAsHtmlString());
      return GitUpdateResult.ERROR;
    }
  }

  public static class ConflictResolver extends GitConflictResolver {
    @NotNull private final GitRebaser myRebaser;
    @NotNull private final VirtualFile myRoot;

    public ConflictResolver(@NotNull Project project, @NotNull Git git, @NotNull VirtualFile root, @NotNull GitRebaser rebaser) {
      super(project, Collections.singleton(root), makeParams(project));
      myRebaser = rebaser;
      myRoot = root;
    }

    private static Params makeParams(Project project) {
      Params params = new Params(project);
      params.setReverse(true);
      params.setMergeDescription("Merge conflicts detected. Resolve them before continuing rebase.");
      params.setErrorNotificationTitle("Can't continue rebase");
      params.setErrorNotificationAdditionalDescription("Then you may <b>continue rebase</b>. <br/> You also may <b>abort rebase</b> to restore the original branch and stop rebasing.");
      return params;
    }

    @Override protected boolean proceedIfNothingToMerge() {
      return myRebaser.continueRebase(myRoot);
    }

    @Override protected boolean proceedAfterAllMerged() {
      return myRebaser.continueRebase(myRoot);
    }
  }
}
