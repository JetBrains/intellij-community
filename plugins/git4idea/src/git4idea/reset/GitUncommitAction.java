// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.reset;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLogUi;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackBase;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitSingleCommitEditingAction;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static git4idea.GitNotificationIdsHolder.COULD_NOT_LOAD_CHANGES_OF_COMMIT;
import static git4idea.reset.GitResetMode.SOFT;
import static java.util.Collections.singletonMap;

public class GitUncommitAction extends GitSingleCommitEditingAction {
  private static final Logger LOG = Logger.getInstance(GitUncommitAction.class);

  @Override
  protected void update(@NotNull AnActionEvent e, @NotNull SingleCommitEditingData commitEditingData) {
    if (e.getPresentation().isEnabledAndVisible()) {
      VcsLogUi logUi = commitEditingData.getLogUi();
      // DataPack is unavailable during refresh
      DataPackBase dataPackBase = ((VisiblePack)logUi.getDataPack()).getDataPack();
      if (!(dataPackBase instanceof DataPack)) {
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(false);
        return;
      }

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
      ChangeListChooser chooser = new ChangeListChooser(project, null, null,
                                                        GitBundle.message("git.undo.action.select.target.changelist.title"),
                                                        commit.getSubject());
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
          changesInCommit = getChangesInCommit(data, commit);
        }
        catch (VcsException e) {
          String message = GitBundle.message("git.undo.action.could.not.load.changes.of.commit", commit.getId().asString());
          LOG.warn(message, e);
          VcsNotifier.getInstance(project).notifyError(COULD_NOT_LOAD_CHANGES_OF_COMMIT,
                                                       "",
                                                       message);
          return;
        }

        // TODO change notification title
        new GitResetOperation(project, singletonMap(repository, commit.getParents().get(0)), SOFT, indicator).execute();

        if (targetChangeList != null) {
          ChangeListManager changeListManager = ChangeListManager.getInstance(project);
          changeListManager.invokeAfterUpdateWithModal(true,
                                                       GitBundle.message("git.undo.action.refreshing.changes.process"), () -> {
              Collection<Change> changes = GitUtil.findCorrespondentLocalChanges(changeListManager, changesInCommit);
              changeListManager.moveChangesTo(targetChangeList, changes.toArray(new Change[0]));
            }
          );
        }
      }
    }.queue();
  }

  @NotNull
  private static Collection<Change> getChangesInCommit(@NotNull VcsLogData data,
                                                       @NotNull VcsShortCommitDetails commit) throws VcsException {
    Hash hash = commit.getId();
    VirtualFile root = commit.getRoot();
    VcsFullCommitDetails details = getChangesFromCache(data, hash, root);
    if (details == null) {
      details = VcsLogUtil.getDetails(data, root, hash);
    }
    return details.getChanges();
  }

  @Nullable
  private static VcsFullCommitDetails getChangesFromCache(@NotNull VcsLogData data, @NotNull Hash hash, @NotNull VirtualFile root) {
    Ref<VcsFullCommitDetails> details = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> details.set(data.getCommitDetailsGetter().getCommitDataIfAvailable(data.getCommitIndex(hash, root))));
    if (details.isNull() || details.get() instanceof LoadingDetails) return null;
    return details.get();
  }
}
