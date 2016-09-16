/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.util;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Executes an action surrounding it with freezing-unfreezing of the ChangeListManager
 * and blocking/unblocking save/sync on frame de/activation.
 */
public class GitFreezingProcess {

  private static final Logger LOG = Logger.getInstance(GitFreezingProcess.class);

  @NotNull private final String myOperationTitle;
  @NotNull private final Runnable myRunnable;

  @NotNull private final ChangeListManagerEx myChangeListManager;
  @NotNull private final ProjectManagerEx myProjectManager;
  @NotNull private final SaveAndSyncHandler mySaveAndSyncHandler;

  public GitFreezingProcess(@NotNull Project project, @NotNull String operationTitle, @NotNull Runnable runnable) {
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
    ((ChangeListManagerImpl)myChangeListManager).freeze("Local changes are not available until Git " + myOperationTitle + " is finished.");
  }

  private void unfreeze() {
    myChangeListManager.letGo();
  }
}
