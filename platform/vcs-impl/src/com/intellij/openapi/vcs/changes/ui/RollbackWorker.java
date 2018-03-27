/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.*;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RollbackWorker {
  private final Project myProject;
  private final String myOperationName;
  private final boolean myInvokedFromModalContext;
  private final List<VcsException> myExceptions;

  public RollbackWorker(final Project project) {
    this(project, DefaultRollbackEnvironment.ROLLBACK_OPERATION_NAME, false);
  }

  public RollbackWorker(final Project project, final String operationName, boolean invokedFromModalContext) {
    myProject = project;
    myOperationName = operationName;
    myInvokedFromModalContext = invokedFromModalContext;
    myExceptions = new ArrayList<>(0);
  }

  public void doRollback(final Collection<Change> changes,
                         final boolean deleteLocallyAddedFiles,
                         @Nullable final Runnable afterVcsRefreshInAwt,
                         @Nullable final String localHistoryActionName) {
    ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
    Collection<LocalChangeList> affectedChangelists = changeListManager.getAffectedLists(changes);

    final Runnable afterRefresh = () -> {
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

    List<Change> otherChanges = revertPartialChanges(changes);

    final Runnable rollbackAction = new MyRollbackRunnable(otherChanges, deleteLocallyAddedFiles, afterRefresh, localHistoryActionName);

    if (ApplicationManager.getApplication().isDispatchThread() && !myInvokedFromModalContext) {
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, myOperationName, true,
                                     new PerformInBackgroundOption() {
                                       public boolean shouldStartInBackground() {
                                         return VcsConfiguration.getInstance(myProject).PERFORM_ROLLBACK_IN_BACKGROUND;
                                       }

                                       public void processSentToBackground() {
                                         VcsConfiguration.getInstance(myProject).PERFORM_ROLLBACK_IN_BACKGROUND = true;
                                       }
                                     }) {
          public void run(@NotNull ProgressIndicator indicator) {
            rollbackAction.run();
          }
        });
    }
    else if (myInvokedFromModalContext) {
      ProgressManager.getInstance().run(new Task.Modal(myProject, myOperationName, true) {
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
  }

  @NotNull
  private List<Change> revertPartialChanges(Collection<Change> changes) {
    return PartialChangesUtil.processPartialChanges(
      myProject, changes, true,
      (partialChanges, tracker) -> {
        for (ChangeListChange change : partialChanges) {
          tracker.rollbackChangelistChanges(change.getChangeListId());
        }
        return true;
      }
    );
  }

  private class MyRollbackRunnable implements Runnable {
    private final Collection<Change> myChanges;
    private final boolean myDeleteLocallyAddedFiles;
    private final Runnable myAfterRefresh;
    private final String myLocalHistoryActionName;
    private ProgressIndicator myIndicator;

    private MyRollbackRunnable(final Collection<Change> changes,
                               final boolean deleteLocallyAddedFiles,
                               final Runnable afterRefresh,
                               final String localHistoryActionName) {
      myChanges = changes;
      myDeleteLocallyAddedFiles = deleteLocallyAddedFiles;
      myAfterRefresh = afterRefresh;
      myLocalHistoryActionName = localHistoryActionName;
    }

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
        myIndicator.startNonCancelableSection();
        myIndicator.setIndeterminate(true);
        myIndicator.setText2("");
        myIndicator.setText(VcsBundle.message("progress.text.synchronizing.files"));
      }

      doRefresh(myProject, changesToRefresh);
      AbstractVcsHelper.getInstance(myProject).showErrors(myExceptions, myOperationName);
    }

    private void doRefresh(final Project project, final List<Change> changesToRefresh) {
      final LocalHistoryAction action = LocalHistory.getInstance().startAction(myOperationName);

      final Runnable forAwtThread = () -> {
        action.finish();
        LocalHistory.getInstance().putSystemLabel(myProject, (myLocalHistoryActionName == null) ?
                                                                                           myOperationName : myLocalHistoryActionName, -1);
        final VcsDirtyScopeManager manager = project.getComponent(VcsDirtyScopeManager.class);
        VcsGuess vcsGuess = new VcsGuess(myProject);

        for (Change change : changesToRefresh) {
          final ContentRevision beforeRevision = change.getBeforeRevision();
          final ContentRevision afterRevision = change.getAfterRevision();
          if ((!change.isIsReplaced()) && beforeRevision != null && Comparing.equal(beforeRevision, afterRevision)) {
            manager.fileDirty(beforeRevision.getFile());
          }
          else {
            markDirty(manager, vcsGuess, beforeRevision);
            markDirty(manager, vcsGuess, afterRevision);
          }
        }

        myAfterRefresh.run();
      };

      RefreshVFsSynchronously.updateChangesForRollback(changesToRefresh);

      WaitForProgressToShow.runOrInvokeLaterAboveProgress(forAwtThread, null, project);
    }

    private void markDirty(@NotNull VcsDirtyScopeManager manager, @NotNull VcsGuess vcsGuess, @Nullable ContentRevision revision) {
      if (revision != null) {
        FilePath parent = revision.getFile().getParentPath();
        if (parent != null && couldBeMarkedDirty(vcsGuess, parent)) {
          manager.dirDirtyRecursively(parent);
        }
        else {
          manager.fileDirty(revision.getFile());
        }
      }
    }

    private boolean couldBeMarkedDirty(@NotNull VcsGuess vcsGuess, @NotNull FilePath path) {
      return vcsGuess.getVcsForDirty(path) != null;
    }

    private void deleteAddedFilesLocally(final List<Change> changes) {
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
