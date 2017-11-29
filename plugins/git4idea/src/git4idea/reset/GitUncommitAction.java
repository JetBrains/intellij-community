/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsShortCommitDetails;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.data.DataPackBase;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogData;
import com.intellij.vcs.log.impl.VcsLogUtil;
import com.intellij.vcs.log.visible.VisiblePack;
import git4idea.GitUtil;
import git4idea.rebase.GitCommitEditingAction;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION;
import static com.intellij.util.ObjectUtils.notNull;
import static git4idea.reset.GitResetMode.SOFT;
import static java.util.Collections.singletonMap;

public class GitUncommitAction extends GitCommitEditingAction {
  private static final Logger LOG = Logger.getInstance(GitUncommitAction.class);
  private static final String FAILURE_TITLE = "Can't Undo Commit";

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
        e.getPresentation().setDescription("The selected commit is not the last in the current branch");
      }
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    super.actionPerformed(e);

    Project project = notNull(e.getProject());
    VcsShortCommitDetails commit = getSelectedCommit(e);
    ChangeListChooser chooser = new ChangeListChooser(project, ChangeListManager.getInstance(project).getChangeListsCopy(),
                                                      null, "Select Target Changelist", commit.getSubject());
    chooser.show();
    LocalChangeList selectedList = chooser.getSelectedList();
    if (selectedList != null) {
      resetInBackground(getLogData(e), getRepository(e), commit, selectedList);
    }
  }

  @NotNull
  @Override
     protected String getFailureTitle() {
    return FAILURE_TITLE;
  }

  private static void resetInBackground(@NotNull VcsLogData data,
                                        @NotNull GitRepository repository,
                                        @NotNull VcsShortCommitDetails commit,
                                        @NotNull LocalChangeList changeList) {
    Project project = repository.getProject();
    new Task.Backgroundable(project, "Undoing Last Commit...", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Collection<Change> changesInCommit;
        try {
          changesInCommit = getChangesInCommit(data, commit);
        }
        catch (VcsException e) {
          String message = "Couldn't load changes of " + commit.getId().asString();
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
          changeListManager.moveChangesTo(changeList, ArrayUtil.toObjectArray(changes, Change.class));
        }, InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE, "Refreshing Changes...", ModalityState.defaultModalityState());
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
    ApplicationManager.getApplication().invokeAndWait(() -> {
      details.set(data.getCommitDetailsGetter().getCommitDataIfAvailable(data.getCommitIndex(hash, root)));
    });
    if (details.isNull() || details.get() instanceof LoadingDetails) return null;
    return details.get();
  }
}
