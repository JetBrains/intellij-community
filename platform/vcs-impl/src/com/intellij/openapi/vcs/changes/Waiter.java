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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsBundle;
import org.jetbrains.annotations.NotNull;

public class Waiter extends Task.Modal {
  private final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.Waiter");
  private final ModalityState myState;
  private final Runnable myRunnable;
  private boolean myStarted;
  private boolean myDone;
  private final Object myLock = new Object();

  public Waiter(final Project project, final Runnable runnable, final ModalityState state, final String title, final boolean cancellable) {
    super(project, title, cancellable);
    myRunnable = runnable;
    myState = state;
    myDone = false;
    myStarted = false;
    setCancelText("Skip");
  }

  public void run(@NotNull ProgressIndicator indicator) {
    indicator.setIndeterminate(true);
    indicator.setText2(VcsBundle.message("commit.wait.util.synched.text"));
    synchronized (myLock) {
      if (myStarted) {
        LOG.error("Waiter running under progress being started again.");
        return;
      }
      myStarted = true;
      while (! myDone) {
        try {
          // every second check whether we are canceled
          myLock.wait(500);
        }
        catch (InterruptedException e) {
          // ok
        }
        indicator.checkCanceled();
      }
    }
  }

  @Override
  public void onCancel() {
    onSuccess();
  }

  @Override
  public void onSuccess() {
    // allow do not wait for done
    /*synchronized (myLock) {
      if (! myDone) {
        return;
      }
    }*/
    if (myProject.isDisposed()) return;
    myRunnable.run();
    ChangesViewManager.getInstance(myProject).refreshView();
  }

  public void done() {
    synchronized (myLock) {
      myDone = true;
      myLock.notifyAll();
    }
  }
}
