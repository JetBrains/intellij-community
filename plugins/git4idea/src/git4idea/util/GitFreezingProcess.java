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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.util.continuation.GatheringContinuationContext;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Executes an action surrounding it with freezing-unfreezing operations.
 * It is a simple linear alternative to {@link git4idea.update.GitComplexProcess} performing tasks in a single method instead of
 * using continuations logic.
 *
 * @author Kirill Likhodedov
 */
public class GitFreezingProcess {

  @NotNull private final String myOperationTitle;
  @NotNull private final Runnable myRunnable;
  @NotNull private final ChangeListManager myChangeListManager;

  public GitFreezingProcess(@NotNull Project project, @NotNull String operationTitle, @NotNull Runnable runnable) {
    myOperationTitle = operationTitle;
    myRunnable = runnable;
    myChangeListManager = ChangeListManager.getInstance(project);
  }

  public void execute() {
    try {
      saveAndBlockInAwt();
      freeze();
      try {
        myRunnable.run();
      }
      finally {
        unfreezeInAwt();
      }
    }
    finally {
      unblockInAwt();
    }
  }

  public static void saveAndBlock() {
    ProjectManagerEx.getInstanceEx().blockReloadingProjectOnExternalChanges();
    FileDocumentManager.getInstance().saveAllDocuments();
    SaveAndSyncHandler.getInstance().blockSaveOnFrameDeactivation();
    SaveAndSyncHandler.getInstance().blockSyncOnFrameActivation();
  }

  private static void saveAndBlockInAwt() {
    RethrowingRunnable rethrowingRunnable = new RethrowingRunnable(new Runnable() {
      @Override public void run() {
        saveAndBlock();
      }
    });
    UIUtil.invokeAndWaitIfNeeded(rethrowingRunnable);
    rethrowingRunnable.rethrowIfHappened();
  }

  private static void unblockInAwt() {
    RethrowingRunnable rethrowingRunnable = new RethrowingRunnable(new Runnable() {
      @Override public void run() {
        unblock();
      }
    });
    UIUtil.invokeAndWaitIfNeeded(rethrowingRunnable);
    rethrowingRunnable.rethrowIfHappened();
  }

  public static void unblock() {
    ProjectManagerEx.getInstanceEx().unblockReloadingProjectOnExternalChanges();
    SaveAndSyncHandler.getInstance().unblockSaveOnFrameDeactivation();
    SaveAndSyncHandler.getInstance().unblockSyncOnFrameActivation();
  }

  private void freeze() {
    myChangeListManager.freeze(new GatheringContinuationContext(),
                               "Local changes are not available until Git " + myOperationTitle + " is finished.");
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
