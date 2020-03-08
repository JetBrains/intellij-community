// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.reset;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackBase;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.util.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import git4idea.GitUtil;
import git4idea.i18n.GitBundle;
import git4idea.rebase.GitCommitEditingAction;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

import static com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION;
import static git4idea.reset.GitResetMode.SOFT;
import static java.util.Collections.singletonMap;

public class GitUncommitAction extends GitCommitEditingAction {
  private static final Logger LOG = Logger.getInstance(GitUncommitAction.class);

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    if (e.getPresentation().isEnabledAndVisible()) {
      // DataPack is unavailable during refresh
      DataPackBase dataPackBase = ((VisiblePack)getUi(e).getDataPack()).getDataPack();
      if (!(dataPackBase instanceof DataPack)) {
        e.getPresentation().setVisible(true);
        e.getPresentation().setEnabled(false);
        return;
      }

      // support undo only for the last commit in the branch
      if (isHeadCommit(e)) {
        e.getPresentation().setEnabled(true);
      }
      else {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription(GitBundle.message("git.undo.action.description"));
      }
    }
  }

  @Override
  public void actionPerformedAfterChecks(@NotNull AnActionEvent e) {
    Project project = Objects.requireNonNull(e.getProject());
    VcsShortCommitDetails commit = getSelectedCommit(e);
    ChangeListChooser chooser = new ChangeListChooser(project, ChangeListManager.getInstance(project).getChangeListsCopy(),
                                                      null, GitBundle.message("git.undo.action.select.target.changelist.title"), commit.getSubject());
    chooser.show();
    LocalChangeList selectedList = chooser.getSelectedList();
    if (selectedList != null) {
      resetInBackground(getLogData(e), getRepository(e), commit, selectedList);
    }
  }

  @NotNull
  @Override
  protected String getFailureTitle() {
    return GitBundle.message("git.undo.action.cant.undo.commit.failure");
  }

  private static void resetInBackground(@NotNull VcsLogData data,
                                        @NotNull GitRepository repository,
                                        @NotNull VcsShortCommitDetails commit,
                                        @NotNull LocalChangeList changeList) {
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
          Notification notification = STANDARD_NOTIFICATION.createNotification("", message, NotificationType.ERROR, null);
          VcsNotifier.getInstance(project).notify(notification);
          return;
        }

        // TODO change notification title
        new GitResetOperation(project, singletonMap(repository, commit.getParents().get(0)), SOFT, indicator).execute();

        ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(project);
        changeListManager.invokeAfterUpdate(() -> {
          Collection<Change> changes = GitUtil.findCorrespondentLocalChanges(changeListManager, changesInCommit);
          changeListManager.moveChangesTo(changeList, changes.toArray(new Change[0]));
        }, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, GitBundle.message("git.undo.action.refreshing.changes.process"), ModalityState.defaultModalityState());
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
