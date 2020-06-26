// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.impl.PartialChangesUtil;
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.intellij.util.ObjectUtils.notNull;

public class RollbackWorker {
  private final Project myProject;
  private final String myOperationName;
  private final boolean myInvokedFromModalContext;
  private final List<VcsException> myExceptions;

  public RollbackWorker(final Project project) {
    this(project, DefaultRollbackEnvironment.getRollbackOperationText(), false);
  }

  public RollbackWorker(final Project project, final String operationName, boolean invokedFromModalContext) {
    myProject = project;
    myOperationName = operationName;
    myInvokedFromModalContext = invokedFromModalContext;
    myExceptions = new ArrayList<>(0);
  }

  public void doRollback(@NotNull Collection<? extends Change> changes,
                         boolean deleteLocallyAddedFiles) {
    doRollback(changes, deleteLocallyAddedFiles, null, null);
  }

  public void doRollback(@NotNull Collection<? extends Change> changes,
                         boolean deleteLocallyAddedFiles,
                         @Nullable Runnable afterVcsRefreshInAwt,
                         @Nullable String localHistoryActionName) {
    doRollback(changes, deleteLocallyAddedFiles, afterVcsRefreshInAwt, localHistoryActionName, false);
  }

  public void doRollback(@NotNull Collection<? extends Change> changes,
                         boolean deleteLocallyAddedFiles,
                         @Nullable Runnable afterVcsRefreshInAwt,
                         @Nullable String localHistoryActionName,
                         boolean honorExcludedFromCommit) {
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
      Collection<LocalChangeList> affectedChangelists = changeListManager.getAffectedLists(changes);

      final LocalHistoryAction action = LocalHistory.getInstance().startAction(myOperationName);

      final Runnable afterRefresh = () -> {
        action.finish();
        LocalHistory.getInstance().putSystemLabel(myProject, notNull(localHistoryActionName, myOperationName), -1);

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
        }, updateMode, "Refresh changelists after update", ModalityState.current());
      };

      List<Change> otherChanges = revertPartialChanges(changes, honorExcludedFromCommit);
      if (otherChanges.isEmpty()) {
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(afterRefresh, null, myProject);
        return;
      }

      final Runnable rollbackAction = new MyRollbackRunnable(otherChanges, deleteLocallyAddedFiles, afterRefresh);

      if (ApplicationManager.getApplication().isDispatchThread() && !myInvokedFromModalContext) {
        ProgressManager.getInstance().run(new Task.Backgroundable(myProject, myOperationName, false,
                                                                  VcsConfiguration.getInstance(myProject).getRollbackOption()) {
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

  @NotNull
  private List<Change> revertPartialChanges(@NotNull Collection<? extends Change> changes, boolean honorExcludedFromCommit) {
    return PartialChangesUtil.processPartialChanges(
      myProject, changes, true,
      (partialChanges, tracker) -> {
        if (!tracker.hasPartialChangesToCommit()) return false;
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
              myIndicator.setText(vcs.getDisplayName() + ": performing " + StringUtil.toLowerCase(myOperationName) + "...");
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
      final Runnable forAwtThread = () -> {
        VcsDirtyScopeManager manager = VcsDirtyScopeManager.getInstance(myProject);
        ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);

        for (Change change : changesToRefresh) {
          final ContentRevision beforeRevision = change.getBeforeRevision();
          final ContentRevision afterRevision = change.getAfterRevision();
          if ((!change.isIsReplaced()) && beforeRevision != null && Comparing.equal(beforeRevision, afterRevision)) {
            manager.fileDirty(beforeRevision.getFile());
          }
          else {
            markDirty(manager, vcsManager, beforeRevision);
            markDirty(manager, vcsManager, afterRevision);
          }
        }

        myAfterRefresh.run();
      };

      RefreshVFsSynchronously.updateChangesForRollback(changesToRefresh);

      WaitForProgressToShow.runOrInvokeLaterAboveProgress(forAwtThread, null, project);
    }

    private void markDirty(@NotNull VcsDirtyScopeManager manager,
                           @NotNull ProjectLevelVcsManager vcsManager,
                           @Nullable ContentRevision revision) {
      if (revision != null) {
        FilePath parent = revision.getFile().getParentPath();
        if (parent != null && couldBeMarkedDirty(vcsManager, parent)) {
          manager.dirDirtyRecursively(parent);
        }
        else {
          manager.fileDirty(revision.getFile());
        }
      }
    }

    private boolean couldBeMarkedDirty(@NotNull ProjectLevelVcsManager vcsGuess, @NotNull FilePath path) {
      return vcsGuess.getVcsFor(path) != null;
    }

    private void deleteAddedFilesLocally(final List<? extends Change> changes) {
      if (myIndicator != null) {
        myIndicator.setText("Deleting added files locally...");
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
            myIndicator.setFraction(((double) i) / changesSize);
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
