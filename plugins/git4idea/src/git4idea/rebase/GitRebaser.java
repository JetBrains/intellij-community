// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.history.ActivityId;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.GitActivity;
import git4idea.GitUtil;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.update.GitUpdateResult;
import git4idea.util.GitUntrackedFilesHelper;
import git4idea.util.LocalChangesWouldBeOverwrittenHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static git4idea.GitNotificationIdsHolder.*;
import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.CHECKOUT;

public final class GitRebaser {

  private static final Logger LOG = Logger.getInstance(GitRebaser.class);

  private final @NotNull Project myProject;
  private final @NotNull Git myGit;
  private final @NotNull ProgressIndicator myProgressIndicator;

  public GitRebaser(@NotNull Project project, @NotNull Git git, @NotNull ProgressIndicator progressIndicator) {
    myProject = project;
    myGit = git;
    myProgressIndicator = progressIndicator;
  }

  public GitUpdateResult rebase(@NotNull VirtualFile root, @NotNull List<String> parameters) {
    return rebase(root, parameters, GitActivity.Rebase);
  }

  @ApiStatus.Internal
  public GitUpdateResult rebase(@NotNull VirtualFile root, @NotNull List<String> parameters, @Nullable ActivityId activityId) {
    final GitLineHandler rebaseHandler = new GitLineHandler(myProject, root, GitCommand.REBASE, GitImpl.REBASE_CONFIG_PARAMS);
    rebaseHandler.setStdoutSuppressed(false);
    rebaseHandler.addParameters(parameters);

    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    rebaseHandler.addLineListener(rebaseConflictDetector);
    GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector = new GitUntrackedFilesOverwrittenByOperationDetector(root);
    GitLocalChangesWouldBeOverwrittenDetector localChangesDetector = new GitLocalChangesWouldBeOverwrittenDetector(root, CHECKOUT);
    rebaseHandler.addLineListener(untrackedFilesDetector);
    rebaseHandler.addLineListener(localChangesDetector);
    rebaseHandler.addLineListener(GitStandardProgressAnalyzer.createListener(myProgressIndicator));

    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, GitBundle.message("activity.name.rebase"), activityId)) {
      GitRebaseEditorHandler editor = GitRebaseUtils.createRebaseEditor(myProject, root, false);
      try (GitHandlerRebaseEditorManager ignored = GitHandlerRebaseEditorManager.prepareEditor(rebaseHandler, editor)) {
        String oldText = myProgressIndicator.getText();
        myProgressIndicator.setText(GitBundle.message("rebase.progress.indicator.title"));
        GitCommandResult result = myGit.runCommand(rebaseHandler);
        myProgressIndicator.setText(oldText);
        return result.success() ?
               GitUpdateResult.SUCCESS :
               handleRebaseFailure(rebaseHandler, root, result, rebaseConflictDetector, untrackedFilesDetector, localChangesDetector);
      }
    }
    catch (ProcessCanceledException pce) {
      return GitUpdateResult.CANCEL;
    }
  }

  public void abortRebase(@NotNull VirtualFile root) {
    LOG.info("abortRebase " + root);

    final GitLineHandler rh = new GitLineHandler(myProject, root, GitCommand.REBASE);
    rh.setStdoutSuppressed(false);
    rh.addParameters("--abort");

    String oldText = myProgressIndicator.getText();
    myProgressIndicator.setText(GitBundle.message("rebase.update.project.abort.task.title"));
    GitCommandResult commandResult = myGit.runCommand(rh);
    myProgressIndicator.setText(oldText);

    if (commandResult.success()) {
      VcsNotifier.getInstance(myProject).notifySuccess(REBASE_ABORT, "",
                                                       GitBundle.message("rebase.update.project.notification.abort.success.message"));
    }
    else {
      VcsNotifier.getInstance(myProject).notifyError(REBASE_ABORT, "",
                                                     GitBundle.message("rebase.update.project.notification.abort.error.message"));
    }

    myProgressIndicator.setText2(GitBundle.message("progress.details.refreshing.files.for.root", root.getPath()));
    root.refresh(false, true);

  }

  public boolean continueRebase(@NotNull VirtualFile root) {
    return continueRebase(root, false);
  }

  private boolean skipCommitAndContinue(@NotNull VirtualFile root) {
    return continueRebase(root, true);
  }

  /**
   * Runs 'git rebase --continue' on several roots consequently.
   *
   * @return true if rebase successfully finished.
   */
  public boolean continueRebase(@NotNull Collection<? extends VirtualFile> rebasingRoots) {
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(myProject, GitBundle.message("activity.name.rebase"), GitActivity.Rebase)) {
      boolean success = true;
      for (VirtualFile root : rebasingRoots) {
        success &= continueRebase(root);
      }
      return success;
    }
  }

  // start operation may be "--continue" or "--skip" depending on the situation.
  private boolean continueRebase(final @NotNull VirtualFile root, boolean skip) {
    LOG.info(String.format("continueRebase in %s, skip: %s", root, skip));
    final GitLineHandler rh = new GitLineHandler(myProject, root, GitCommand.REBASE, GitImpl.REBASE_CONFIG_PARAMS);
    rh.setStdoutSuppressed(false);
    rh.addParameters(skip ? "--skip" : "--continue");

    final GitRebaseProblemDetector rebaseConflictDetector = new GitRebaseProblemDetector();
    rh.addLineListener(rebaseConflictDetector);
    rh.addLineListener(GitStandardProgressAnalyzer.createListener(myProgressIndicator));

    GitRebaseEditorHandler editor = GitRebaseUtils.createRebaseEditor(myProject, root, false);
    try (GitHandlerRebaseEditorManager ignored = GitHandlerRebaseEditorManager.prepareEditor(rh, editor)) {
      String oldText = myProgressIndicator.getText();
      myProgressIndicator.setText(GitBundle.message("rebase.progress.indicator.title"));
      GitCommandResult result = myGit.runCommand(rh);
      myProgressIndicator.setText(oldText);
      if (result.success()) return true;

      return handleRebaseContinueFailure(root, result, rebaseConflictDetector);
    }
    catch (ProcessCanceledException pce) {
      return false;
    }
  }

  /**
   * @return true if the failure situation was resolved successfully, false if we failed to resolve the problem.
   */
  private boolean handleRebaseContinueFailure(final VirtualFile root,
                                              @NotNull GitCommandResult commandResult,
                                              @NotNull GitRebaseProblemDetector rebaseConflictDetector) {
    if (rebaseConflictDetector.isMergeConflict()) {
      LOG.info("handleRebaseFailure merge conflict");
      return new GitRebaser.ResumeConflictResolver(myProject, root, this).merge();
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
          LOG.info("no changes confirmed. Skipping commit " + GitRebaseUtils.getCurrentRebaseCommit(myProject, root));
          return skipCommitAndContinue(root);
        }
      }
      catch (VcsException e) {
        LOG.info("Failed to work around 'no changes' error.", e);
        VcsNotifier.getInstance(myProject)
          .notifyError(
            REBASE_UPDATE_PROJECT_ERROR,
            GitBundle.message("rebase.update.project.notification.failed.title"),
            GitBundle.message("rebase.update.project.notification.failed.message", e.getMessage()));
        return false;
      }
    }
    else {
      List<VcsException> errors = ContainerUtil.map(collectErrorOutputLines(commandResult), it -> new VcsException(it));
      LOG.info("handleRebaseFailure error");
      VcsNotifier.getInstance(myProject)
        .notifyError(
          REBASE_UPDATE_PROJECT_ERROR,
          GitBundle.message("rebase.update.project.notification.failed.title"),
          "",
          errors);
      return false;
    }
  }

  private static @NotNull List<@NlsSafe String> collectErrorOutputLines(@NotNull GitCommandResult result) {
    List<String> errors = new ArrayList<>();
    errors.addAll(ContainerUtil.filter(result.getOutput(), line -> GitHandlerUtil.isErrorLine(line.trim())));
    errors.addAll(ContainerUtil.filter(result.getErrorOutput(), line -> GitHandlerUtil.isErrorLine(line.trim())));

    if (errors.isEmpty() && !result.success()) {
      errors.addAll(result.getErrorOutput());
      if (errors.isEmpty()) {
        List<String> output = result.getOutput();
        String lastOutput = ContainerUtil.findLast(output, line -> !StringUtil.isEmptyOrSpaces(line));
        return Collections.singletonList(lastOutput);
      }
    }
    return errors;
  }

  private void stageEverything(@NotNull VirtualFile root) throws VcsException {
    GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.ADD);
    handler.setSilent(false);
    handler.addParameters("--update");
    myGit.runCommand(handler).throwOnError();
  }

  private static @NotNull GitConflictResolver.Params makeParams(@NotNull Project project) {
    return new GitConflictResolver.Params(project)
      .setReverse(true)
      .setErrorNotificationTitle(GitBundle.message("rebase.update.project.conflict.error.notification.title"))
      .setMergeDescription(GitBundle.message("rebase.update.project.conflict.merge.description.label"))
      .setErrorNotificationAdditionalDescription(GitBundle.message("rebase.update.project.conflict.error.notification.description"));
  }

  public @NotNull GitUpdateResult handleRebaseFailure(@NotNull GitLineHandler handler,
                                                      @NotNull VirtualFile root,
                                                      @NotNull GitCommandResult result,
                                                      @NotNull GitRebaseProblemDetector rebaseConflictDetector,
                                                      @NotNull GitMessageWithFilesDetector untrackedWouldBeOverwrittenDetector,
                                                      @NotNull GitLocalChangesWouldBeOverwrittenDetector localChangesDetector) {
    if (rebaseConflictDetector.isMergeConflict()) {
      LOG.info("handleRebaseFailure merge conflict");
      final boolean allMerged = new RebaserConflictResolver(myProject, root, this).merge();
      return allMerged ? GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS : GitUpdateResult.INCOMPLETE;
    }
    else if (untrackedWouldBeOverwrittenDetector.isDetected()) {
      LOG.info("handleRebaseFailure: untracked files would be overwritten by checkout");
      GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(myProject, root,
                                                                untrackedWouldBeOverwrittenDetector.getRelativeFilePaths(),
                                                                GitBundle.message("rebase.operation.name"), null);
      return GitUpdateResult.ERROR;
    }
    else if (localChangesDetector.isDetected()) {
      LocalChangesWouldBeOverwrittenHelper.showErrorNotification(
        myProject,
        LOCAL_CHANGES_DETECTED,
        root,
        GitBundle.message("rebase.git.operation.name"),
        localChangesDetector.getRelativeFilePaths()
      );
      return GitUpdateResult.ERROR;
    }
    else {
      LOG.info("handleRebaseFailure error");
      VcsNotifier.getInstance(myProject).notifyError(REBASE_UPDATE_PROJECT_ERROR,
                                                     GitBundle.message("rebase.update.project.notification.failed.title"),
                                                     result.getErrorOutputAsHtmlString(),
                                                     true);
      return GitUpdateResult.ERROR;
    }
  }

  private static class RebaserConflictResolver extends GitConflictResolver {
    private final @NotNull GitRebaser myRebaser;
    private final @NotNull VirtualFile myRoot;

    private RebaserConflictResolver(@NotNull Project project, @NotNull VirtualFile root, @NotNull GitRebaser rebaser) {
      super(project, Collections.singleton(root), makeParams(project));
      myRebaser = rebaser;
      myRoot = root;
    }

    @Override
    protected boolean proceedIfNothingToMerge() {
      return myRebaser.continueRebase(myRoot);
    }

    @Override
    protected boolean proceedAfterAllMerged() {
      return myRebaser.continueRebase(myRoot);
    }
  }

  private static class ResumeConflictResolver extends GitConflictResolver {
    private final @NotNull GitRebaser myRebaser;
    private final @NotNull VirtualFile myRoot;

    private ResumeConflictResolver(@NotNull Project project, @NotNull VirtualFile root, @NotNull GitRebaser rebaser) {
      super(project, Collections.singleton(root), makeParams(project));
      myRebaser = rebaser;
      myRoot = root;
    }

    @Override
    protected boolean proceedIfNothingToMerge() {
      notifyUnresolvedRemain();
      return false;
    }

    @Override
    protected boolean proceedAfterAllMerged() {
      return myRebaser.continueRebase(myRoot);
    }
  }
}
