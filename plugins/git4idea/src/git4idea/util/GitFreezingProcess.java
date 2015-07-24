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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.util.ui.UIUtil;
import git4idea.GitPlatformFacade;
import org.jetbrains.annotations.NotNull;

/**
 * Executes an action surrounding it with freezing-unfreezing of the ChangeListManager
 * and blocking/unblocking save/sync on frame de/activation.
 */
public class GitFreezingProcess {

  private static final Logger LOG = Logger.getInstance(GitFreezingProcess.class);

  @NotNull private final GitPlatformFacade myFacade;
  @NotNull private final String myOperationTitle;
  @NotNull private final Runnable myRunnable;
  @NotNull private final ChangeListManagerEx myChangeListManager;

  public GitFreezingProcess(@NotNull Project project, @NotNull GitPlatformFacade facade,
                            @NotNull String operationTitle, @NotNull Runnable runnable) {
    myFacade = facade;
    myOperationTitle = operationTitle;
    myRunnable = runnable;
    myChangeListManager = myFacade.getChangeListManager(project);
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
        unfreezeInAwt();
      }
    }
    finally {
      LOG.debug("unblocking project autosync");
      unblockInAwt();
    }
    LOG.debug("finished.");
  }

  public static void saveAndBlock(@NotNull GitPlatformFacade platformFacade) {
    platformFacade.getProjectManager().blockReloadingProjectOnExternalChanges();
    platformFacade.saveAllDocuments();
    platformFacade.getSaveAndSyncHandler().blockSaveOnFrameDeactivation();
    platformFacade.getSaveAndSyncHandler().blockSyncOnFrameActivation();
  }

  private void saveAndBlockInAwt() {
    RethrowingRunnable rethrowingRunnable = new RethrowingRunnable(new Runnable() {
      @Override public void run() {
        saveAndBlock(myFacade);
      }
    });
    UIUtil.invokeAndWaitIfNeeded(rethrowingRunnable);
    rethrowingRunnable.rethrowIfHappened();
  }

  private void unblockInAwt() {
    RethrowingRunnable rethrowingRunnable = new RethrowingRunnable(new Runnable() {
      @Override public void run() {
        unblock(myFacade);
      }
    });
    UIUtil.invokeAndWaitIfNeeded(rethrowingRunnable);
    rethrowingRunnable.rethrowIfHappened();
  }

  public static void unblock(@NotNull GitPlatformFacade platformFacade) {
    platformFacade.getProjectManager().unblockReloadingProjectOnExternalChanges();
    platformFacade.getSaveAndSyncHandler().unblockSaveOnFrameDeactivation();
    platformFacade.getSaveAndSyncHandler().unblockSyncOnFrameActivation();
  }

  private void freeze() {
    myChangeListManager.freezeImmediately("Local changes are not available until Git " + myOperationTitle + " is finished.");
  }

  private void unfreeze() {
    myChangeListManager.letGo();
  }

  private void unfreezeInAwt() {
    RethrowingRunnable rethrowingRunnable = new RethrowingRunnable(new Runnable() {
      @Override public void run() {
        unfreeze();
      }
    });
    UIUtil.invokeAndWaitIfNeeded(rethrowingRunnable);
    rethrowingRunnable.rethrowIfHappened();
  }

  // if an error happens, let it be thrown in the calling thread (in awt actually)
  // + throw it in this thread afterwards, to be able to execute the finally block.
  private static class RethrowingRunnable implements Runnable {

    private final Runnable myRunnable;
    private RuntimeException myException;

    RethrowingRunnable(@NotNull Runnable runnable) {
      myRunnable = runnable;
    }

    @Override
    public void run() {
      try {
        myRunnable.run();
      }
      catch (Throwable t) {
        RuntimeException re = new RuntimeException(t);
        myException = re;
        throw re;
      }
    }

    void rethrowIfHappened() {
      if (myException != null) {
        throw myException;
      }
    }
  }

}
