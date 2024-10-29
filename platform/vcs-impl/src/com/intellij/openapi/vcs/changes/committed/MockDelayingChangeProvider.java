// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.changes.ChangeListManagerGate;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.changes.ChangelistBuilder;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class MockDelayingChangeProvider implements ChangeProvider {
  private final Object myLock;
  private boolean myLocked;
  private Thread myExecuteInsideUpdate;

  public MockDelayingChangeProvider() {
    myLock = new Object();
  }

  @Override
  public void getChanges(@NotNull final VcsDirtyScope dirtyScope, @NotNull final ChangelistBuilder builder, @NotNull final ProgressIndicator progress,
                         @NotNull final ChangeListManagerGate addGate) {
    synchronized (myLock) {
      if (myExecuteInsideUpdate == null) {
        return;
      }

      myLocked = true;
      myExecuteInsideUpdate.start();

      while (myLocked) {
        try {
          myLock.wait();
        }
        catch (InterruptedException e) {
          //
        }
      }
    }
  }

  public void setTest(final Runnable runnable) {
    synchronized (myLock) {
      if (runnable == null) {
        myExecuteInsideUpdate = null;
      } else {
        myExecuteInsideUpdate = new Thread(() -> {
          // wait until starter sleeps
          synchronized (myLock) {
            runnable.run();
          }
        }, "vcs delaying execute");
      }
    }
  }

  public void unlock() {
    synchronized (myLock) {
      myLocked = false;
      myLock.notifyAll();
    }
  }

  @Override
  public boolean isModifiedDocumentTrackingRequired() {
    return false;
  }
}
