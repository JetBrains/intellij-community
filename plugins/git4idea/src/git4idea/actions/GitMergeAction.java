// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.AbstractCommonUpdateAction;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.vcs.ViewUpdateInfoNotification;
import git4idea.*;
import git4idea.branch.GitBranchPair;
import git4idea.commands.*;
import git4idea.merge.GitConflictResolver;
import git4idea.merge.GitMergeCommittingConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.merge.MergeChangeCollector;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitUpdateInfoAsLog;
import git4idea.update.GitUpdatedRanges;
import git4idea.util.GitUIUtil;
import git4idea.util.GitUntrackedFilesHelper;
import git4idea.util.LocalChangesWouldBeOverwrittenHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.notification.NotificationType.INFORMATION;
import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.MERGE;
import static git4idea.update.GitUpdateSessionKt.getBodyForUpdateNotification;
import static git4idea.update.GitUpdateSessionKt.getTitleForUpdateNotification;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

abstract class GitMergeAction extends GitRepositoryAction {
  private static final Logger LOG = Logger.getInstance(GitMergeAction.class);

  protected static class DialogState {
    final VirtualFile selectedRoot;
    final String progressTitle;
    final Computable<GitLineHandler> handlerProvider;
    @NotNull private final List<String> selectedBranches;

    DialogState(@NotNull VirtualFile root,
                @NotNull String title,
                @NotNull Computable<GitLineHandler> provider,
                @NotNull List<String> selectedBranches) {
      selectedRoot = root;
      progressTitle = title;
      handlerProvider = provider;
      this.selectedBranches = selectedBranches;
    }
  }

  @Nullable
  protected abstract DialogState displayDialog(@NotNull Project project, @NotNull List<VirtualFile> gitRoots,
                                               @NotNull VirtualFile defaultRoot);

  @Override
  protected void perform(@NotNull Project project, @NotNull List<VirtualFile> gitRoots, @NotNull VirtualFile defaultRoot) {
    DialogState dialogState = displayDialog(project, gitRoots, defaultRoot);
    if (dialogState == null) {
      return;
    }
    VirtualFile selectedRoot = dialogState.selectedRoot;
    Computable<GitLineHandler> handlerProvider = dialogState.handlerProvider;
    Label beforeLabel = LocalHistory.getInstance().putSystemLabel(project, "Before update");

    new Task.Backgroundable(project, dialogState.progressTitle, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        Git git = Git.getInstance();
        GitLocalChangesWouldBeOverwrittenDetector localChangesDetector = new GitLocalChangesWouldBeOverwrittenDetector(selectedRoot, MERGE);
        GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector =
          new GitUntrackedFilesOverwrittenByOperationDetector(selectedRoot);
        GitSimpleEventDetector mergeConflict = new GitSimpleEventDetector(GitSimpleEventDetector.Event.MERGE_CONFLICT);

        GitRepository repository = repositoryManager.getRepositoryForRoot(selectedRoot);
        assert repository != null : "Repository can't be null for root " + selectedRoot;

        GitUpdatedRanges updatedRanges = null;
        if (repository.getCurrentBranch() != null && dialogState.selectedBranches.size() == 1) {
          String selectedBranch = StringUtil.trimStart(dialogState.selectedBranches.get(0), "remotes/");
          GitBranch targetBranch = repository.getBranches().findBranchByName(selectedBranch);
          if (targetBranch != null) {
            GitBranchPair refPair = new GitBranchPair(repository.getCurrentBranch(), targetBranch);
            updatedRanges = GitUpdatedRanges.calcInitialPositions(project, singletonMap(repository, refPair));
          }
          else {
            LOG.warn("Couldn't find the branch with name [" + selectedBranch + "]");
          }
        }

        try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(project, getActionName())) {
          GitCommandResult result = git.runCommand(() -> {
            GitLineHandler handler = handlerProvider.compute();
            handler.addLineListener(localChangesDetector);
            handler.addLineListener(untrackedFilesDetector);
            handler.addLineListener(mergeConflict);
            return handler;
          });

          String revision = repository.getCurrentRevision();
          if (revision == null) {
            return;
          }

          GitRevisionNumber currentRev = new GitRevisionNumber(revision);
          handleResult(result, project, mergeConflict, localChangesDetector, untrackedFilesDetector, repository, currentRev, beforeLabel,
                       updatedRanges);
        }
      }
    }.queue();
  }

  private void handleResult(@NotNull GitCommandResult result,
                            @NotNull Project project,
                            @NotNull GitSimpleEventDetector mergeConflictDetector,
                            @NotNull GitLocalChangesWouldBeOverwrittenDetector localChangesDetector,
                            @NotNull GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector,
                            @NotNull GitRepository repository,
                            @NotNull GitRevisionNumber currentRev,
                            @NotNull Label beforeLabel,
                            @Nullable GitUpdatedRanges updatedRanges) {
    VirtualFile root = repository.getRoot();

    if (mergeConflictDetector.hasHappened()) {
      new GitMergeCommittingConflictResolver(project, Git.getInstance(), new GitMerger(project), singletonList(root),
                                             new GitConflictResolver.Params(project), true).merge();
    }

    if (result.success() || mergeConflictDetector.hasHappened()) {
      VfsUtil.markDirtyAndRefresh(false, true, false, root);
      repository.update();
      if (updatedRanges != null && AbstractCommonUpdateAction.showsCustomNotification(singletonList(GitVcs.getInstance(project)))) {
        new GitUpdateInfoAsLog(project, updatedRanges.calcCurrentPositions(), (filesCount, commitCount, filteredCommits, viewCommits) -> {
          String title = getTitleForUpdateNotification(filesCount, commitCount);
          String content = getBodyForUpdateNotification(filesCount, commitCount, filteredCommits);
          Notification notification = VcsNotifier.STANDARD_NOTIFICATION.createNotification(title, content, INFORMATION, null);
          notification.addAction(NotificationAction.createSimple("View Commits", viewCommits));
          return notification;
        }).buildAndShowNotification();
      }
      else {
        List<VcsException> exceptions = new ArrayList<>();
        showUpdates(project, exceptions, root, currentRev, beforeLabel, getActionName());
        GitVcs.getInstance(project).showErrors(exceptions, getActionName());
      }
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
      repository.update();
    }
  }

  private static void showUpdates(@NotNull Project project,
                                  @NotNull List<? super VcsException> exceptions,
                                  @NotNull VirtualFile root,
                                  @NotNull GitRevisionNumber currentRev,
                                  @NotNull Label beforeLabel,
                                  @NotNull String actionName) {
    UpdatedFiles files = UpdatedFiles.create();
    MergeChangeCollector collector = new MergeChangeCollector(project, root, currentRev);
    collector.collect(files, exceptions);
    if (!exceptions.isEmpty()) return;

    GuiUtils.invokeLaterIfNeeded(() -> {
      ProjectLevelVcsManagerEx manager = (ProjectLevelVcsManagerEx)ProjectLevelVcsManager.getInstance(project);
      UpdateInfoTree tree = manager.showUpdateProjectInfo(files, actionName, ActionInfo.UPDATE, false);
      if (tree != null) {
        tree.setBefore(beforeLabel);
        tree.setAfter(LocalHistory.getInstance().putSystemLabel(project, "After update"));
        ViewUpdateInfoNotification.focusUpdateInfoTree(project, tree);
      }
    }, ModalityState.defaultModalityState());
  }
}
