// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.history.ActivityId;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.impl.PartialChangesUtil;
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.util.ObjectUtils;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.VcsActivity;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class RollbackWorker {
  private static final Logger LOG = Logger.getInstance(RollbackWorker.class);

  private final Project myProject;
  private final @Nls(capitalization = Nls.Capitalization.Title) String myOperationName;
  private final boolean myInvokedFromModalContext;
  private final List<VcsException> myExceptions;

  public RollbackWorker(final Project project) {
    this(project, DefaultRollbackEnvironment.getRollbackOperationText(), false);
  }

  public RollbackWorker(final Project project,
                        @Nls(capitalization = Nls.Capitalization.Title) String operationName,
                        boolean invokedFromModalContext) {
    myProject = project;
    myOperationName = operationName;
    myInvokedFromModalContext = invokedFromModalContext;
    myExceptions = new ArrayList<>(0);
  }

  public void doRollback(@NotNull Collection<? extends Change> changes,
                         boolean deleteLocallyAddedFiles) {
    doRollback(changes, deleteLocallyAddedFiles, VcsBundle.message("activity.name.rollback"), VcsActivity.Rollback);
  }

  public void doRollback(@NotNull Collection<? extends Change> changes,
                         boolean deleteLocallyAddedFiles,
                         @Nullable Runnable afterVcsRefreshInAwt,
                         @Nullable @Nls String localHistoryActionName) {
    doRollback(changes, deleteLocallyAddedFiles, afterVcsRefreshInAwt,
               ObjectUtils.chooseNotNull(localHistoryActionName, VcsBundle.message("activity.name.rollback")),
               VcsActivity.Rollback, false);
  }

  public void doRollback(@NotNull Collection<? extends Change> changes,
                         boolean deleteLocallyAddedFiles,
                         @NotNull @NlsContexts.Label String localHistoryActionName,
                         @NotNull ActivityId activityId) {
    doRollback(changes, deleteLocallyAddedFiles, null, localHistoryActionName, activityId, false);
  }

  public void doRollback(@NotNull Collection<? extends Change> changes,
                         boolean deleteLocallyAddedFiles,
                         @Nullable Runnable afterVcsRefreshInAwt,
                         @NotNull @NlsContexts.Label String localHistoryActionName,
                         @Nullable ActivityId activityId,
                         boolean honorExcludedFromCommit) {
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
      Collection<LocalChangeList> affectedChangelists = changeListManager.getAffectedLists(changes);

      LocalHistoryAction action = activityId != null ? LocalHistory.getInstance().startAction(localHistoryActionName, activityId) : null;

      final Runnable afterRefresh = () -> {
        if (action != null) action.finish();
        LocalHistory.getInstance().putSystemLabel(myProject, localHistoryActionName, -1);

        InvokeAfterUpdateMode updateMode = myInvokedFromModalContext ?
                                           InvokeAfterUpdateMode.SYNCHRONOUS_CANCELLABLE :
                                           InvokeAfterUpdateMode.SILENT;
        changeListManager.invokeAfterUpdate(() -> {
          for (LocalChangeList list : affectedChangelists) {
            changeListManager.scheduleAutomaticEmptyChangeListDeletion(list);
          }

          if (afterVcsRefreshInAwt != null) {
            afterVcsRefreshInAwt.run();
          }
        }, updateMode, VcsBundle.message("changes.refresh.changelists.after.update"), ModalityState.current());
      };

      List<Change> otherChanges = revertPartialChanges(changes, honorExcludedFromCommit);
      if (otherChanges.isEmpty()) {
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(afterRefresh, null, myProject);
        return;
      }

      final Runnable rollbackAction = new MyRollbackRunnable(otherChanges, deleteLocallyAddedFiles, afterRefresh);

      if (ApplicationManager.getApplication().isDispatchThread() && !myInvokedFromModalContext) {
        ProgressManager.getInstance().run(new Task.Backgroundable(myProject, myOperationName, false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            rollbackAction.run();
          }
        });
      }
      else if (myInvokedFromModalContext) {
        ProgressManager.getInstance().run(new Task.Modal(myProject, myOperationName, false) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            rollbackAction.run();
          }
        });
      }
      else {
        rollbackAction.run();
      }
      changeListManager.showLocalChangesInvalidated();
    });
  }

  private @NotNull List<Change> revertPartialChanges(@NotNull Collection<? extends Change> changes, boolean honorExcludedFromCommit) {
    return PartialChangesUtil.processPartialChanges(
      myProject, changes, true,
      (partialChanges, tracker) -> {
        if (!tracker.hasPartialChangesToCommit()) return false;
        if (!tracker.isOperational()) {
          LOG.warn("Skipping non-operational tracker: " + tracker);
          return false;
        }

        if (!honorExcludedFromCommit) {
          Set<String> selectedIds = ContainerUtil.map2Set(partialChanges, change -> change.getChangeListId());
          if (selectedIds.containsAll(tracker.getAffectedChangeListsIds())) return false;
        }

        List<String> changelistIds = ContainerUtil.map(partialChanges, change -> change.getChangeListId());
        tracker.rollbackChanges(changelistIds, honorExcludedFromCommit);
        return true;
      }
    );
  }

  private final class MyRollbackRunnable implements Runnable {
    private final Collection<? extends Change> myChanges;
    private final boolean myDeleteLocallyAddedFiles;
    private final Runnable myAfterRefresh;
    private ProgressIndicator myIndicator;

    private MyRollbackRunnable(final Collection<? extends Change> changes,
                               final boolean deleteLocallyAddedFiles,
                               final Runnable afterRefresh) {
      myChanges = changes;
      myDeleteLocallyAddedFiles = deleteLocallyAddedFiles;
      myAfterRefresh = afterRefresh;
    }

    @Override
    public void run() {
      ChangesUtil.markInternalOperation(myChanges, true);
      try {
        doRun();
      }
      finally {
        ChangesUtil.markInternalOperation(myChanges, false);
      }
    }

    private void doRun() {
      myIndicator = ProgressManager.getInstance().getProgressIndicator();

      final List<Change> changesToRefresh = new ArrayList<>();
      try {
        ChangesUtil.processChangesByVcs(myProject, myChanges, (vcs, changes) -> {
          final RollbackEnvironment environment = vcs.getRollbackEnvironment();
          if (environment != null) {
            changesToRefresh.addAll(changes);

            if (myIndicator != null) {
              myIndicator.setText(VcsBundle.message("changes.progress.text.vcs.name.performing.operation.name", vcs.getDisplayName(),
                                                    StringUtil.toLowerCase(myOperationName)));
              myIndicator.setIndeterminate(false);
              myIndicator.checkCanceled();
            }
            environment.rollbackChanges(changes, myExceptions, new RollbackProgressModifier(changes.size(), myIndicator));
            if (myIndicator != null) {
              myIndicator.setText2("");
              myIndicator.checkCanceled();
            }

            if (myExceptions.isEmpty() && myDeleteLocallyAddedFiles) {
              deleteAddedFilesLocally(changes);
            }
          }
        });
      }
      catch (ProcessCanceledException e) {
        // still do refresh
      }

      if (myIndicator != null) {
        myIndicator.setIndeterminate(true);
        myIndicator.setText2("");
        myIndicator.setText(VcsBundle.message("progress.text.synchronizing.files"));
      }

      doRefresh(myProject, changesToRefresh);
      if (!myExceptions.isEmpty()) {
        AbstractVcsHelper.getInstance(myProject).showErrors(myExceptions, myOperationName);
      }
    }

    private void doRefresh(final Project project, final List<? extends Change> changesToRefresh) {

      VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
      ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
      for (FilePath filePath : ChangesUtil.iteratePaths(changesToRefresh)) {
        markDirty(filePath, vcsManager, dirtyScopeManager);
      }

      RefreshVFsSynchronously.updateChangesForRollback(changesToRefresh);

      WaitForProgressToShow.runOrInvokeLaterAboveProgress(myAfterRefresh, null, project);
    }

    private static void markDirty(@NotNull FilePath filePath,
                                  @NotNull ProjectLevelVcsManager vcsManager,
                                  @NotNull VcsDirtyScopeManager dirtyScopeManager) {
      AbstractVcs vcs = vcsManager.getVcsFor(filePath);
      if (vcs == null) return;

      if (vcs.areDirectoriesVersionedItems()) {
        FilePath parentPath = filePath.getParentPath();
        if (parentPath != null && vcsManager.getVcsFor(parentPath) == vcs) {
          dirtyScopeManager.dirDirtyRecursively(parentPath);
          return;
        }
      }

      dirtyScopeManager.fileDirty(filePath);
    }

    private void deleteAddedFilesLocally(final List<? extends Change> changes) {
      if (myIndicator != null) {
        myIndicator.setText(VcsBundle.message("changes.deleting.added.files.locally"));
        myIndicator.setFraction(0);
      }
      final int changesSize = changes.size();
      for (int i = 0; i < changesSize; i++) {
        final Change c = changes.get(i);
        if (c.getType() == Change.Type.NEW) {
          ContentRevision rev = c.getAfterRevision();
          assert rev != null;
          final File ioFile = rev.getFile().getIOFile();
          if (myIndicator != null) {
            myIndicator.setText2(ioFile.getAbsolutePath());
            myIndicator.setFraction(((double)i) / changesSize);
          }
          FileUtil.delete(ioFile);
        }
      }
      if (myIndicator != null) {
        myIndicator.setText2("");
      }
    }
  }
}
