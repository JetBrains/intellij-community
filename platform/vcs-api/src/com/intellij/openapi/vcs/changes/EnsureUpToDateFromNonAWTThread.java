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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

/**
 * for non-AWT threads, synchronously waits for completion of ChanegListManager update
 */
public class EnsureUpToDateFromNonAWTThread {
  private final Project myProject;
  private volatile boolean myDone;

  public EnsureUpToDateFromNonAWTThread(final Project project) {
    //assert ApplicationManager.getApplication().isUnitTestMode();
    assert ! ApplicationManager.getApplication().isDispatchThread();
    myProject = project;
    myDone = false;
  }

  public void execute() {
    assert ! ApplicationManager.getApplication().isDispatchThread();
    final Object lock = new Object();

    synchronized (lock) {
      ChangeListManager.getInstance(myProject).invokeAfterUpdate(new Runnable() {
        public void run() {
          synchronized (lock) {
            myDone = true;
            lock.notifyAll();
          }
        }
      }, InvokeAfterUpdateMode.SILENT, null, null);

      while ((! myDone) && (! myProject.isDisposed())) {
        try {
          lock.wait();
        }
        catch (InterruptedException e) {
          //
        }
      }
    }
  }

  public boolean isDone() {
    return myDone;
  }
}
