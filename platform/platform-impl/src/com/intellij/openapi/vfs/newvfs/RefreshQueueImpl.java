/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.io.storage.HeavyProcessLatch;
import gnu.trove.TLongObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.Collections;
import java.util.concurrent.ExecutorService;

/**
 * @author max
 */
public class RefreshQueueImpl extends RefreshQueue implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.RefreshQueueImpl");

  private final ExecutorService myQueue = new BoundedTaskExecutor(PooledThreadExecutor.INSTANCE, 1, this);
  private final ProgressIndicator myRefreshIndicator = RefreshProgress.create(VfsBundle.message("file.synchronize.progress"));
  private final TLongObjectHashMap<RefreshSession> mySessions = new TLongObjectHashMap<>();
  private final FrequentEventDetector myEventCounter = new FrequentEventDetector(100, 100, FrequentEventDetector.Level.WARN);

  public void execute(@NotNull RefreshSessionImpl session) {
    if (session.isAsynchronous()) {
      queueSession(session, session.getModalityState(), session.getTransaction());
    }
    else {
      Application app = ApplicationManager.getApplication();
      if (app.isDispatchThread()) {
        ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
        doScan(session);
        session.fireEvents();
      }
      else {
        if (((ApplicationEx)app).holdsReadLock()) {
          LOG.error("Do not call synchronous refresh under read lock (except from EDT) - " +
                    "this will cause a deadlock if there are any events to fire.");
          return;
        }
        queueSession(session, ModalityState.defaultModalityState(), TransactionGuard.getInstance().getContextTransaction());
        session.waitFor();
      }
    }
  }

  private void queueSession(@NotNull final RefreshSessionImpl session, @NotNull final ModalityState modality, @Nullable TransactionId transaction) {
    myQueue.submit(() -> {
      myRefreshIndicator.start();
      try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Doing file refresh. " + session)) {
        doScan(session);
      }
      finally {
        myRefreshIndicator.stop();
        Application app = ApplicationManager.getApplication();
        // invokeLater might be not necessary once transactions are enforced
        app.invokeLater(
          () -> TransactionGuard.getInstance().submitTransaction(app, transaction, session::fireEvents),
          modality);
      }
    });
    myEventCounter.eventHappened(session);
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

  @NotNull
  @Override
  public RefreshSession createSession(boolean async, boolean recursively, @Nullable Runnable finishRunnable, @NotNull ModalityState state) {
    return new RefreshSessionImpl(async, recursively, finishRunnable, state);
  }

  @Override
  public void processSingleEvent(@NotNull VFileEvent event) {
    new RefreshSessionImpl(Collections.singletonList(event)).launch();
  }

  public static boolean isRefreshInProgress() {
    RefreshQueueImpl refreshQueue = (RefreshQueueImpl)RefreshQueue.getInstance();
    synchronized (refreshQueue.mySessions) {
      return !refreshQueue.mySessions.isEmpty();
    }
  }

  @Override
  public void dispose() { }
}