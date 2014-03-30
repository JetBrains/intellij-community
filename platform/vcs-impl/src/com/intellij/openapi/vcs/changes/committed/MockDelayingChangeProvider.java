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
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.List;

public class MockDelayingChangeProvider implements ChangeProvider {
  private final Object myLock;
  private boolean myLocked;
  private Thread myExecuteInsideUpdate;

  public MockDelayingChangeProvider() {
    myLock = new Object();
  }

  public void getChanges(final VcsDirtyScope dirtyScope, final ChangelistBuilder builder, final ProgressIndicator progress,
                         final ChangeListManagerGate addGate)
    throws VcsException {
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
        myExecuteInsideUpdate = new Thread(new Runnable() {
          public void run() {
            // wait until starter sleeps
            synchronized (myLock) {
              runnable.run();
            }
          }
        });
      }
    }
  }

  public void unlock() {
    synchronized (myLock) {
      myLocked = false;
      myLock.notifyAll();
    }
  }

  public boolean isModifiedDocumentTrackingRequired() {
    return false;
  }

  public void doCleanup(final List<VirtualFile> files) {
  }
}
