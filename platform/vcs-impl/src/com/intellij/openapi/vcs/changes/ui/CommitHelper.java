// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.actions.MoveChangesToAnotherListAction;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.application.ApplicationManager.getApplication;
import static com.intellij.openapi.progress.ProgressManager.progress;
import static com.intellij.openapi.ui.Messages.getQuestionIcon;
import static com.intellij.openapi.vcs.VcsBundle.message;
import static com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY;
import static com.intellij.openapi.vcs.changes.ChangesUtil.processChangesByVcs;
import static com.intellij.util.ArrayUtil.toObjectArray;
import static com.intellij.util.ObjectUtils.notNull;
import static com.intellij.util.WaitForProgressToShow.runOrInvokeLaterAboveProgress;
import static com.intellij.util.containers.ContainerUtil.newArrayList;
import static com.intellij.util.ui.ConfirmationDialog.requestForConfirmation;
import static java.util.Collections.emptyList;

public class CommitHelper extends AbstractCommitter {
  @NotNull private final LocalChangeList myChangeList;
  @NotNull private final String myActionName;
  private final boolean myAllOfDefaultChangeListChangesIncluded;
  private final boolean myForceSyncCommit;
  @NotNull private final GeneralCommitProcessor myCommitProcessor;

  @SuppressWarnings("unused") // Required for compatibility with external plugins.
  public CommitHelper(@NotNull Project project,
                      @NotNull ChangeList changeList,
                      @NotNull List<? extends Change> includedChanges,
                      @NotNull String actionName,
                      @NotNull String commitMessage,
                      @NotNull List<? extends CheckinHandler> handlers,
                      boolean allOfDefaultChangeListChangesIncluded,
                      boolean synchronously,
                      @NotNull NullableFunction<Object, Object> additionalDataHolder,
                      @Nullable CommitResultHandler customResultHandler) {
    this(project, (LocalChangeList)changeList, includedChanges, actionName, commitMessage, handlers, allOfDefaultChangeListChangesIncluded,
         synchronously, additionalDataHolder, customResultHandler, false, null);
  }

  public CommitHelper(@NotNull Project project,
                      @NotNull LocalChangeList changeList,
                      @NotNull List<? extends Change> includedChanges,
                      @NotNull String actionName,
                      @NotNull String commitMessage,
                      @NotNull List<? extends CheckinHandler> handlers,
                      boolean allOfDefaultChangeListChangesIncluded,
                      boolean synchronously,
                      @NotNull NullableFunction<Object, Object> additionalDataHolder,
                      @Nullable CommitResultHandler resultHandler,
                      boolean isAlien,
                      @Nullable AbstractVcs vcs) {
    super(project, includedChanges, commitMessage, handlers, additionalDataHolder);
    myChangeList = changeList;
    myActionName = actionName;
    myAllOfDefaultChangeListChangesIncluded = allOfDefaultChangeListChangesIncluded;
    myForceSyncCommit = synchronously;
    myCommitProcessor = isAlien ? new AlienCommitProcessor(notNull(vcs)) : new CommitProcessor(vcs);

    addResultHandler(notNull(resultHandler, new DefaultCommitResultHandler(this)));
  }

  @SuppressWarnings("UnusedReturnValue")
  public boolean doCommit() {
    runCommit(myActionName, myForceSyncCommit);
    return true;
  }

  @Override
  protected void commit() {
    myCommitProcessor.commit();
  }

  @Override
  protected void afterCommit() {
    myCommitProcessor.afterCommit();
  }

  @Override
  protected void onSuccess() {
    myCommitProcessor.onSuccess();
  }

  @Override
  protected void onFailure() {
    myCommitProcessor.onFailure();
  }

  @Override
  protected void onFinish() {
    myCommitProcessor.onFinish();
  }

  private class AlienCommitProcessor extends GeneralCommitProcessor {
    @NotNull private final AbstractVcs myVcs;

    private AlienCommitProcessor(@NotNull AbstractVcs vcs) {
      myVcs = vcs;
    }

    @Override
    public void commit() {
      CommitHelper.this.commit(myVcs, getChanges());
    }

    @Override
    public void afterCommit() {
    }

    @Override
    public void onSuccess() {
    }

    @Override
    public void onFailure() {
    }

    @Override
    public void onFinish() {
    }
  }

  abstract static class GeneralCommitProcessor {
    public abstract void commit();

    public abstract void afterCommit();

    public abstract void onSuccess();

    public abstract void onFailure();

    public abstract void onFinish();
  }

  private class CommitProcessor extends GeneralCommitProcessor {
    @NotNull private LocalHistoryAction myAction = LocalHistoryAction.NULL;
    private boolean myCommitSuccess;
    @Nullable private final AbstractVcs myVcs;

    private CommitProcessor(@Nullable AbstractVcs vcs) {
      myVcs = vcs;
    }

    @Override
    public void commit() {
      if (myVcs != null && getChanges().isEmpty()) {
        CommitHelper.this.commit(myVcs, getChanges());
      }
      processChangesByVcs(getProject(), getChanges(), CommitHelper.this::commit);
    }

    @Override
    public void afterCommit() {
      ChangeListManagerImpl.getInstanceImpl(getProject()).showLocalChangesInvalidated();

      myAction = ReadAction.compute(() -> LocalHistory.getInstance().startAction(myActionName));
    }

    @Override
    public void onSuccess() {
      myCommitSuccess = true;
    }

    @Override
    public void onFailure() {
      getApplication().invokeLater(
        () -> moveToFailedList(myChangeList, getCommitMessage(), getFailedToCommitChanges(),
                               message("commit.dialog.failed.commit.template", myChangeList.getName()), getProject()),
        ModalityState.defaultModalityState(), getProject().getDisposed());
    }

    @Override
    public void onFinish() {
      refreshChanges();
      runOrInvokeLaterAboveProgress(() -> doPostRefresh(), null, getProject());
    }

    private void refreshChanges() {
      List<Change> toRefresh = newArrayList();
      processChangesByVcs(getProject(), getChanges(), (vcs, changes) -> {
        CheckinEnvironment environment = vcs.getCheckinEnvironment();
        if (environment != null && environment.isRefreshAfterCommitNeeded()) {
          toRefresh.addAll(changes);
        }
      });

      if (!toRefresh.isEmpty()) {
        progress(message("commit.dialog.refresh.files"));
        RefreshVFsSynchronously.updateChanges(toRefresh);
      }
    }

    private void doPostRefresh() {
      myAction.finish();
      if (!getProject().isDisposed()) {
        // after vcs refresh is completed, outdated notifiers should be removed if some exists...
        VcsDirtyScopeManager.getInstance(getProject()).filePathsDirty(getPathsToRefresh(), null);
        ChangeListManager clManager = ChangeListManager.getInstance(getProject());
        clManager.invokeAfterUpdate(
          () -> {
            if (myCommitSuccess) {
              updateChangelistAfterRefresh();
            }

            CommittedChangesCache cache = CommittedChangesCache.getInstance(getProject());
            // in background since commit must have authorized
            cache.refreshAllCachesAsync(false, true);
            cache.refreshIncomingChangesAsync();
          }, InvokeAfterUpdateMode.SILENT, null, null);

        LocalHistory.getInstance().putSystemLabel(getProject(), myActionName + ": " + getCommitMessage());
      }
    }

    private void updateChangelistAfterRefresh() {
      ChangeListManagerEx clManager = (ChangeListManagerEx)ChangeListManager.getInstance(getProject());
      String listName = myChangeList.getName();

      LocalChangeList localList = clManager.findChangeList(listName);
      if (localList == null) return;

      clManager.editChangeListData(listName, null);

      if (!localList.isDefault()) {
        clManager.scheduleAutomaticEmptyChangeListDeletion(localList);
      }
      else {
        Collection<Change> changes = localList.getChanges();
        if (getConfiguration().OFFER_MOVE_TO_ANOTHER_CHANGELIST_ON_PARTIAL_COMMIT &&
            !changes.isEmpty() &&
            myAllOfDefaultChangeListChangesIncluded) {
          ChangelistMoveOfferDialog dialog = new ChangelistMoveOfferDialog(getConfiguration());
          if (dialog.showAndGet()) {
            MoveChangesToAnotherListAction.askAndMove(getProject(), changes, emptyList());
          }
        }
      }
    }
  }

  @CalledInAwt
  public static void moveToFailedList(@NotNull ChangeList changeList,
                                      @NotNull String commitMessage,
                                      @NotNull List<? extends Change> failedChanges,
                                      @NotNull String newChangelistName,
                                      @NotNull Project project) {
    // No need to move since we'll get exactly the same changelist.
    if (failedChanges.containsAll(changeList.getChanges())) return;

    VcsConfiguration configuration = VcsConfiguration.getInstance(project);
    if (configuration.MOVE_TO_FAILED_COMMIT_CHANGELIST != DO_ACTION_SILENTLY) {
      VcsShowConfirmationOption option = new VcsShowConfirmationOption() {
        @Override
        public Value getValue() {
          return configuration.MOVE_TO_FAILED_COMMIT_CHANGELIST;
        }

        @Override
        public void setValue(Value value) {
          configuration.MOVE_TO_FAILED_COMMIT_CHANGELIST = value;
        }

        @Override
        public boolean isPersistent() {
          return true;
        }
      };
      boolean result =
        requestForConfirmation(option, project, message("commit.failed.confirm.prompt"), message("commit.failed.confirm.title"),
                               getQuestionIcon());
      if (!result) return;
    }

    ChangeListManager changeListManager = ChangeListManager.getInstance(project);
    int index = 1;
    String failedListName = newChangelistName;
    while (changeListManager.findChangeList(failedListName) != null) {
      index++;
      failedListName = newChangelistName + " (" + index + ")";
    }

    LocalChangeList failedList = changeListManager.addChangeList(failedListName, commitMessage);
    changeListManager.moveChangesTo(failedList, toObjectArray(failedChanges, Change.class));
  }
}
