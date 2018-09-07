// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.*;
import git4idea.merge.GitMergeUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUIUtil;
import git4idea.util.GitUntrackedFilesHelper;
import git4idea.util.LocalChangesWouldBeOverwrittenHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.MERGE;

abstract class GitMergeAction extends GitRepositoryAction {

  protected static class DialogState {
    final VirtualFile selectedRoot;
    final String progressTitle;
    final Computable<GitLineHandler> handlerProvider;
    DialogState(@NotNull VirtualFile root, @NotNull String title, @NotNull Computable<GitLineHandler> provider) {
      selectedRoot = root;
      progressTitle = title;
      handlerProvider = provider;
    }
  }

  @Nullable
  protected abstract DialogState displayDialog(@NotNull Project project, @NotNull List<VirtualFile> gitRoots,
                                               @NotNull VirtualFile defaultRoot);

  @Override
  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot) {
    final DialogState dialogState = displayDialog(project, gitRoots, defaultRoot);
    if (dialogState == null) {
      return;
    }
    final VirtualFile selectedRoot = dialogState.selectedRoot;
    final Computable<GitLineHandler> handlerProvider = dialogState.handlerProvider;
    final Label beforeLabel = LocalHistory.getInstance().putSystemLabel(project, "Before update");

    new Task.Backgroundable(project, dialogState.progressTitle, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        final Git git = Git.getInstance();
        final GitLocalChangesWouldBeOverwrittenDetector localChangesDetector =
          new GitLocalChangesWouldBeOverwrittenDetector(selectedRoot, MERGE);
        final GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector =
          new GitUntrackedFilesOverwrittenByOperationDetector(selectedRoot);
        final GitSimpleEventDetector mergeConflict = new GitSimpleEventDetector(GitSimpleEventDetector.Event.MERGE_CONFLICT);

        try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(project, getActionName())) {
          GitCommandResult result = git.runCommand(() -> {
            GitLineHandler handler = handlerProvider.compute();
            handler.addLineListener(localChangesDetector);
            handler.addLineListener(untrackedFilesDetector);
            handler.addLineListener(mergeConflict);
            return handler;
          });

          GitRepository repository = repositoryManager.getRepositoryForRoot(selectedRoot);
          assert repository != null : "Repository can't be null for root " + selectedRoot;
          String revision = repository.getCurrentRevision();
          if (revision == null) {
            return;
          }
          final GitRevisionNumber currentRev = new GitRevisionNumber(revision);
          handleResult(result, project, mergeConflict, localChangesDetector, untrackedFilesDetector,
                       repository, currentRev, beforeLabel);
        }
      }

    }.queue();
  }

  private void handleResult(GitCommandResult result,
                            Project project,
                            GitSimpleEventDetector mergeConflictDetector,
                            GitLocalChangesWouldBeOverwrittenDetector localChangesDetector,
                            GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector,
                            GitRepository repository,
                            GitRevisionNumber currentRev,
                            Label beforeLabel) {
    GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
    VirtualFile root = repository.getRoot();
    if (result.success() || mergeConflictDetector.hasHappened()) {
      VfsUtil.markDirtyAndRefresh(false, true, false, root);
      List<VcsException> exceptions = new ArrayList<>();
      GitMergeUtil.showUpdates(project, exceptions, root, currentRev, beforeLabel, getActionName(), ActionInfo.UPDATE);
      repositoryManager.updateRepository(root);
      showErrors(project, getActionName(), exceptions);
    }
    else if (localChangesDetector.wasMessageDetected()) {
      LocalChangesWouldBeOverwrittenHelper.showErrorNotification(project, repository.getRoot(), getActionName(),
                                                                 localChangesDetector.getRelativeFilePaths());
    }
    else if (untrackedFilesDetector.wasMessageDetected()) {
      GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(project, root, untrackedFilesDetector.getRelativeFilePaths(),
                                                                getActionName(), null);
    }
    else {
      GitUIUtil.notifyError(project, "Git " + getActionName() + " Failed", result.getErrorOutputAsJoinedString(), true, null);
      repositoryManager.updateRepository(root);
    }
  }
}
