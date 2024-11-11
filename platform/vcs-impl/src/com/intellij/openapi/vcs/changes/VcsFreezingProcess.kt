// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.configurationStore.StoreReloadManager;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Executes an action surrounding it with freezing-unfreezing of the ChangeListManager
 * and blocking/unblocking save/sync on frame de/activation.
 */
public class VcsFreezingProcess {
  private static final Logger LOG = Logger.getInstance(VcsFreezingProcess.class);

  @NotNull private final Project myProject;
  @NotNull private final String myOperationTitle;
  @NotNull private final Runnable myRunnable;

  @NotNull private final ChangeListManagerEx myChangeListManager;

  public VcsFreezingProcess(@NotNull Project project, @NotNull @Nls String operationTitle, @NotNull Runnable runnable) {
    myProject = project;
    myOperationTitle = operationTitle;
    myRunnable = runnable;

    myChangeListManager = ChangeListManagerEx.getInstanceEx(project);
  }

  public void execute() {
    LOG.debug("starting");
    try {
      LOG.debug("saving documents, blocking project autosync");
      saveAndBlockInAwt();
      try {
        LOG.debug("freezing the ChangeListManager");
        freeze();
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
      StoreReloadManager.Companion.getInstance(myProject).blockReloadingProjectOnExternalChanges();
      FileDocumentManager.getInstance().saveAllDocuments();

      SaveAndSyncHandler saveAndSyncHandler = SaveAndSyncHandler.getInstance();
      saveAndSyncHandler.blockSaveOnFrameDeactivation();
      saveAndSyncHandler.blockSyncOnFrameActivation();
    });
  }

  private void unblockInAwt() {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      StoreReloadManager.Companion.getInstance(myProject).unblockReloadingProjectOnExternalChanges();

      SaveAndSyncHandler saveAndSyncHandler = SaveAndSyncHandler.getInstance();
      saveAndSyncHandler.unblockSaveOnFrameDeactivation();
      saveAndSyncHandler.unblockSyncOnFrameActivation();
    });
  }

  private void freeze() {
    myProject.getMessageBus().syncPublisher(Listener.TOPIC).onFreeze();
    myChangeListManager.freeze(VcsBundle.message("local.changes.freeze.message", myOperationTitle));
  }

  private void unfreeze() {
    myProject.getMessageBus().syncPublisher(Listener.TOPIC).onUnfreeze();
    myChangeListManager.unfreeze();
  }

  public interface Listener {
    Topic<Listener> TOPIC = Topic.create("Change List Manager Freeze", Listener.class);

    default void onFreeze() {}

    default void onUnfreeze() {}
  }
}
