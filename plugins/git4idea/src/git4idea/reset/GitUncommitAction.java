// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.reset;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.AbstractDataGetter;
import com.intellij.vcs.log.data.VcsLogData;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitSingleCommitEditingAction;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

import static git4idea.GitNotificationIdsHolder.COULD_NOT_LOAD_CHANGES_OF_COMMIT;
import static git4idea.reset.GitResetMode.SOFT;
import static java.util.Collections.singletonMap;

public class GitUncommitAction extends GitSingleCommitEditingAction {
  private static final Logger LOG = Logger.getInstance(GitUncommitAction.class);

  @Override
  protected void update(@NotNull AnActionEvent e, @NotNull SingleCommitEditingData commitEditingData) {
    if (e.getPresentation().isEnabledAndVisible()) {
      // support undo only for the last commit in the branch
      if (commitEditingData.isHeadCommit()) {
        e.getPresentation().setEnabled(true);
      }
      else {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription(GitBundle.message("git.undo.action.description"));
      }
    }
  }

  @Override
  public void actionPerformedAfterChecks(@NotNull SingleCommitEditingData commitEditingData) {
    Project project = commitEditingData.getProject();
    VcsShortCommitDetails commit = commitEditingData.getSelectedCommit();
    LocalChangeList targetList;
    if (ChangeListManager.getInstance(project).areChangeListsEnabled()) {
      ChangeListChooser chooser = new ChangeListChooser(project, GitBundle.message("git.undo.action.select.target.changelist.title"));
      chooser.setSuggestedName(commit.getSubject());
      if (!chooser.showAndGet()) return;

      targetList = chooser.getSelectedList();
    }
    else {
      targetList = null;
    }
    resetInBackground(commitEditingData.getLogData(), commitEditingData.getRepository(), commit, targetList);
  }

  @NotNull
  @Override
  protected String getFailureTitle() {
    return GitBundle.message("git.undo.action.cant.undo.commit.failure");
  }

  private static void resetInBackground(@NotNull VcsLogData data,
                                        @NotNull GitRepository repository,
                                        @NotNull VcsShortCommitDetails commit,
                                        @Nullable LocalChangeList targetChangeList) {
    Project project = repository.getProject();
    new Task.Backgroundable(project, GitBundle.message("git.undo.action.undoing.last.commit.process"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Collection<Change> changesInCommit;
        try {
          changesInCommit = AbstractDataGetter.getCommitDetails(data.getCommitDetailsGetter(),
                                                                commit.getId(), commit.getRoot()).getChanges();
        }
        catch (VcsException e) {
          String message = GitBundle.message("git.undo.action.could.not.load.changes.of.commit", commit.getId().asString());
          LOG.warn(message, e);
          VcsNotifier.getInstance(project).notifyError(COULD_NOT_LOAD_CHANGES_OF_COMMIT,
                                                       "",
                                                       message);
          return;
        }

        GitResetOperation.OperationPresentation presentation = new GitResetOperation.OperationPresentation();
        presentation.activityName = "git.undo.action.process";
        presentation.operationTitle = "git.undo.action.operation";
        presentation.notificationSuccess = "git.undo.action.successful.notification.message";
        presentation.notificationFailure = "git.undo.action.failed.notification.title";

        Map<GitRepository, Hash> targetCommits = singletonMap(repository, commit.getParents().get(0));
        new GitResetOperation(project, targetCommits, SOFT, indicator, presentation).execute();

        if (targetChangeList != null) {
          ChangeListManager changeListManager = ChangeListManager.getInstance(project);
          changeListManager.invokeAfterUpdateWithModal(true,
                                                       GitBundle.message("git.undo.action.refreshing.changes.process"), () -> {
              Collection<Change> changes = GitUtil.findCorrespondentLocalChanges(changeListManager, changesInCommit);
              changeListManager.moveChangesTo(targetChangeList, changes.toArray(Change.EMPTY_CHANGE_ARRAY));
            }
          );
        }
      }
    }.queue();
  }
}
