/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util.continuation;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.concurrency.Semaphore;

/**
 * @author irengrig
 *         Date: 4/7/11
 *         Time: 2:54 PM
 */
public class SameProgressRunner extends GeneralRunner {
  private final Thread myInitThread;
  private final Semaphore mySemaphore;
  private final static Logger LOG = Logger.getInstance("#com.intellij.util.continuation.SameProgressRunner");

  public SameProgressRunner(Project project, boolean cancellable) {
    super(project, cancellable);

    final Application application = ApplicationManager.getApplication();
    if (! application.isUnitTestMode()) {
      assert ! application.isDispatchThread();
    }
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator == null) {
      indicator = new EmptyProgressIndicator();
    }
    setIndicator(indicator);
    myInitThread = Thread.currentThread();
    mySemaphore = new Semaphore();
  }

  @Override
  public void ping() {
    clearSuspend();
    if (Thread.currentThread().equals(myInitThread)) {
      new Runnable() {
        @Override
        public void run() {
          pingInSourceThread();
        }
      }.run();
    } else {
      mySemaphore.up();
    }
  }

  private void pingInSourceThread() {
    while (true) {
      try {
        // stop if project is being disposed
        if (ApplicationManager.getApplication().isDisposed() || ! myProject.isOpen()) return;

        if (getSuspendFlag()) {
          mySemaphore.down();
          while (getSuspendFlag()) {
            mySemaphore.waitFor(500);
          }
        }

        final TaskDescriptor current = getNextMatching();
        if (current == null) {
          return;
        }

        if (Where.AWT.equals(current.getWhere())) {
          WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(new Runnable() {
            @Override
            public void run() {
              current.run(SameProgressRunner.this);
            }
          });
        } else {
          current.run(this);
        }
      } catch (ProcessCanceledException ignored) {
      } catch (Throwable t) {
        LOG.error(t);
        cancelIndicator();
      }
    }
  }
}
