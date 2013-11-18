/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import gnu.trove.TLongObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.concurrent.ExecutorService;

/**
 * @author max
 */
public class RefreshQueueImpl extends RefreshQueue {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.RefreshQueueImpl");

  private final ExecutorService myQueue = ConcurrencyUtil.newSingleThreadExecutor("FS Synchronizer");
  private final ProgressIndicator myRefreshIndicator = RefreshProgress.create(VfsBundle.message("file.synchronize.progress"));
  private final TLongObjectHashMap<RefreshSession> mySessions = new TLongObjectHashMap<RefreshSession>();

  public void execute(@NotNull RefreshSessionImpl session) {
    if (session.isAsynchronous()) {
      ModalityState state = session.getModalityState();
      queueSession(session, state);
    }
    else {
      Application app = ApplicationManager.getApplication();
      if (app.isDispatchThread()) {
        doScan(session);
        session.fireEvents(app.isWriteAccessAllowed());
      }
      else {
        if (((ApplicationEx)app).holdsReadLock()) {
          LOG.error("Do not call synchronous refresh from inside read action except for event dispatch thread. " +
                    "This will eventually cause deadlock if there are any events to fire");
          return;
        }
        queueSession(session, ModalityState.defaultModalityState());
        session.waitFor();
      }
    }
  }

  private void queueSession(@NotNull final RefreshSessionImpl session, @NotNull final ModalityState modality) {
    myQueue.submit(new Runnable() {
      @Override
      public void run() {
        try {
          myRefreshIndicator.start();
          HeavyProcessLatch.INSTANCE.processStarted();
          try {
            doScan(session);
          }
          finally {
            HeavyProcessLatch.INSTANCE.processFinished();
            myRefreshIndicator.stop();
          }
        }
        finally {
          final Application app = ApplicationManager.getApplication();
          app.invokeLater(new DumbAwareRunnable() {
            @Override
            public void run() {
              if (app.isDisposed()) return;
              session.fireEvents(false);
            }
          }, modality);
        }
      }
    });
  }

  private void doScan(RefreshSessionImpl session) {
    try {
      updateSessionMap(session, true);
      session.scan();
    }
    finally {
      updateSessionMap(session, false);
    }
  }

  private void updateSessionMap(RefreshSession session, boolean add) {
    long id = session.getId();
    if (id != 0) {
      synchronized (mySessions) {
        if (add) {
          mySessions.put(id, session);
        }
        else {
          mySessions.remove(id);
        }
      }
    }
  }

  @Override
  public void cancelSession(long id) {
    RefreshSession session;
    synchronized (mySessions) {
      session = mySessions.get(id);
    }
    if (session instanceof RefreshSessionImpl) {
      ((RefreshSessionImpl)session).cancel();
    }
  }

  @Override
  public RefreshSession createSession(boolean async, boolean recursively, @Nullable Runnable finishRunnable, @NotNull ModalityState state) {
    return new RefreshSessionImpl(async, recursively, finishRunnable, state);
  }

  @Override
  public void processSingleEvent(@NotNull VFileEvent event) {
    new RefreshSessionImpl(Collections.singletonList(event)).launch();
  }
}