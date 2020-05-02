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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx;
import com.intellij.openapi.vcs.update.AbstractCommonUpdateAction;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vcs.update.UpdateInfoTree;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import com.intellij.vcs.ViewUpdateInfoNotification;
import git4idea.GitBranch;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.branch.GitBranchPair;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.merge.GitConflictResolver;
import git4idea.merge.GitMerger;
import git4idea.merge.MergeChangeCollector;
import git4idea.rebase.GitHandlerRebaseEditorManager;
import git4idea.rebase.GitInteractiveRebaseEditorHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.update.GitUpdateInfoAsLog;
import git4idea.update.GitUpdatedRanges;
import git4idea.update.HashRange;
import git4idea.util.GitUIUtil;
import git4idea.util.GitUntrackedFilesHelper;
import git4idea.util.LocalChangesWouldBeOverwrittenHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

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
    final boolean commitAfterMerge;

    DialogState(@NotNull VirtualFile root,
                @NotNull String title,
                @NotNull Computable<GitLineHandler> provider,
                @NotNull List<String> selectedBranches,
                boolean commitAfterMerge) {
      selectedRoot = root;
      progressTitle = title;
      handlerProvider = provider;
      this.selectedBranches = selectedBranches;
      this.commitAfterMerge = commitAfterMerge;
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

        String beforeRevision = repository.getCurrentRevision();

        boolean setupRebaseEditor = shouldSetupRebaseEditor(project, selectedRoot);
        Ref<GitHandlerRebaseEditorManager> rebaseEditorManager = Ref.create();
        try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(project, getActionName())) {
          GitCommandResult result = git.runCommand(() -> {
            GitLineHandler handler = handlerProvider.compute();

            if (setupRebaseEditor) {
              if (!rebaseEditorManager.isNull()) {
                rebaseEditorManager.get().close();
              }
              GitInteractiveRebaseEditorHandler editor = new GitInteractiveRebaseEditorHandler(project, selectedRoot);
              rebaseEditorManager.set(GitHandlerRebaseEditorManager.prepareEditor(handler, editor));
            }

            handler.addLineListener(localChangesDetector);
            handler.addLineListener(untrackedFilesDetector);
            handler.addLineListener(mergeConflict);
            return handler;
          });

          if (beforeRevision != null) {
            GitRevisionNumber currentRev = new GitRevisionNumber(beforeRevision);
            handleResult(result, project, mergeConflict, localChangesDetector, untrackedFilesDetector, repository, currentRev, beforeLabel,
                         updatedRanges, dialogState.commitAfterMerge);
          }
        }
        finally {
          if (!rebaseEditorManager.isNull()) {
            rebaseEditorManager.get().close();
          }
        }
      }
    }.queue();
  }

  protected boolean shouldSetupRebaseEditor(@NotNull Project project, VirtualFile selectedRoot) {
    return false;
  }

  private void handleResult(@NotNull GitCommandResult result,
                            @NotNull Project project,
                            @NotNull GitSimpleEventDetector mergeConflictDetector,
                            @NotNull GitLocalChangesWouldBeOverwrittenDetector localChangesDetector,
                            @NotNull GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector,
                            @NotNull GitRepository repository,
                            @NotNull GitRevisionNumber currentRev,
                            @NotNull Label beforeLabel,
                            @Nullable GitUpdatedRanges updatedRanges,
                            boolean commitAfterMerge) {
    VirtualFile root = repository.getRoot();

    if (mergeConflictDetector.hasHappened()) {
      GitMerger merger = new GitMerger(project);
      new GitConflictResolver(project, singletonList(root), new GitConflictResolver.Params(project)) {
        @Override
        protected boolean proceedAfterAllMerged() throws VcsException {
          if (commitAfterMerge) {
            merger.mergeCommit(root);
          }
          return true;
        }
      }.merge();
    }

    if (result.success() || mergeConflictDetector.hasHappened()) {
      GitUtil.refreshVfsInRoot(root);
      repository.update();
      if (updatedRanges != null &&
          AbstractCommonUpdateAction.showsCustomNotification(singletonList(GitVcs.getInstance(project))) &&
          commitAfterMerge) {
        Map<GitRepository, HashRange> ranges = updatedRanges.calcCurrentPositions();
        GitUpdateInfoAsLog.NotificationData notificationData = new GitUpdateInfoAsLog(project, ranges).calculateDataAndCreateLogTab();

        Notification notification;
        if (notificationData != null) {
          String title = getTitleForUpdateNotification(notificationData.getUpdatedFilesCount(), notificationData.getReceivedCommitsCount());
          String content = getBodyForUpdateNotification(notificationData.getFilteredCommitsCount());
          notification = VcsNotifier.STANDARD_NOTIFICATION.createNotification(title, content, INFORMATION, null);
          notification.addAction(NotificationAction.createSimple(GitBundle.messagePointer(
            "action.NotificationAction.GitMergeAction.text.view.commits"),
                                                                 notificationData.getViewCommitAction()));
        }
        else {
          notification = VcsNotifier.STANDARD_NOTIFICATION.createNotification(VcsBundle.message("message.text.all.files.are.up.to.date"),
                                                                              "", INFORMATION, null);
        }
        VcsNotifier.getInstance(project).notify(notification);
      }
      else {
        showUpdates(project, repository, currentRev, beforeLabel, getActionName());
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
                                  @NotNull GitRepository repository,
                                  @NotNull GitRevisionNumber currentRev,
                                  @NotNull Label beforeLabel,
                                  @NotNull String actionName) {
    try {
      UpdatedFiles files = UpdatedFiles.create();
      MergeChangeCollector collector = new MergeChangeCollector(project, repository, currentRev);
      collector.collect(files);

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
    catch (VcsException e) {
      GitVcs.getInstance(project).showErrors(singletonList(e), actionName);
    }
  }
}
