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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.concurrent.ExecutorService;

public class RefreshQueueImpl extends RefreshQueue {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.RefreshQueueImpl");

  private final ExecutorService myQueue = ConcurrencyUtil.newSingleThreadExecutor("FS Synchronizer");
  private final ProgressIndicator myRefreshIndicator = new RefreshProgress(VfsBundle.message("file.synchronize.progress"));

  public void execute(final RefreshSessionImpl session) {
    if (session.isAsynchronous()) {
      ModalityState state = session.getModalityState();
      queueSession(session, state != null ? state : ModalityState.NON_MODAL);
    }
    else {
      final Application app = ApplicationManager.getApplication();
      boolean isEDT = app.isDispatchThread();
      final boolean hasWriteAction = app.isWriteAccessAllowed();
      if (isEDT || hasWriteAction) {
        session.scan();
        session.fireEvents(hasWriteAction);
      }
      else {
        if (((ApplicationEx)app).holdsReadLock()) {
          LOG.error("Do not call synchronous refresh from inside read action except for event dispatch thread. This will eventually cause deadlock if there are events to fire");
          return;
        }

        queueSession(session, ModalityState.defaultModalityState());
        session.waitFor();
      }
    }
  }

  private void queueSession(final RefreshSessionImpl session, final ModalityState modality) {
    myQueue.submit(new Runnable() {
      public void run() {
        try {
          myRefreshIndicator.start();
          HeavyProcessLatch.INSTANCE.processStarted();
          try {
            session.scan();
          }
          finally {
            HeavyProcessLatch.INSTANCE.processFinished();
            myRefreshIndicator.stop();
          }
        }
        finally {
          final Application app = ApplicationManager.getApplication();
          app.invokeLater(new DumbAwareRunnable() {
            public void run() {
              if (app.isDisposed()) return;
              session.fireEvents(false);
            }
          }, modality);
        }
      }
    });
  }

  public RefreshSession createSession(final boolean async, boolean recursively, @Nullable final Runnable finishRunnable, ModalityState state) {
    return new RefreshSessionImpl(async, recursively, finishRunnable, state);
  }

  public void processSingleEvent(VFileEvent event) {
    new RefreshSessionImpl(Collections.singletonList(event)).launch();
  }
}