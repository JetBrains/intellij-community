// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import org.jetbrains.annotations.NotNull;

/**
 * Executes an action surrounding it with freezing-unfreezing of the ChangeListManager
 * and blocking/unblocking save/sync on frame de/activation.
 */
public class VcsFreezingProcess {

  private static final Logger LOG = Logger.getInstance(VcsFreezingProcess.class);

  @NotNull private final String myOperationTitle;
  @NotNull private final Runnable myRunnable;

  @NotNull private final ChangeListManagerEx myChangeListManager;
  @NotNull private final ProjectManagerEx myProjectManager;
  @NotNull private final SaveAndSyncHandler mySaveAndSyncHandler;

  public VcsFreezingProcess(@NotNull Project project, @NotNull String operationTitle, @NotNull Runnable runnable) {
    myOperationTitle = operationTitle;
    myRunnable = runnable;

    myChangeListManager = (ChangeListManagerEx)ChangeListManager.getInstance(project);
    myProjectManager = ProjectManagerEx.getInstanceEx();
    mySaveAndSyncHandler = SaveAndSyncHandler.getInstance();
  }

  public void execute() {
    LOG.debug("starting");
    try {
      LOG.debug("saving documents, blocking project autosync");
      saveAndBlockInAwt();
      LOG.debug("freezing the ChangeListManager");
      freeze();
      try {
        LOG.debug("running the operation");
        myRunnable.run();
        LOG.debug("operation completed.");
      }
      finally {
        LOG.debug("unfreezing the ChangeListManager");
        unfreeze();
      }
    }
    finally {
      LOG.debug("unblocking project autosync");
      unblockInAwt();
    }
    LOG.debug("finished.");
  }

  private void saveAndBlockInAwt() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      myProjectManager.blockReloadingProjectOnExternalChanges();
      FileDocumentManager.getInstance().saveAllDocuments();
      mySaveAndSyncHandler.blockSaveOnFrameDeactivation();
      mySaveAndSyncHandler.blockSyncOnFrameActivation();
    });
  }

  private void unblockInAwt() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      myProjectManager.unblockReloadingProjectOnExternalChanges();
      mySaveAndSyncHandler.unblockSaveOnFrameDeactivation();
      mySaveAndSyncHandler.unblockSyncOnFrameActivation();
    });
  }

  private void freeze() {
    myChangeListManager.freeze("Local changes are not available until " + myOperationTitle + " is finished.");
  }

  private void unfreeze() {
    myChangeListManager.unfreeze();
  }
}
