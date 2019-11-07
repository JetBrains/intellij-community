// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import gnu.trove.TLongObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * @author max
 */
public class RefreshQueueImpl extends RefreshQueue implements Disposable {
  private static final Logger LOG = Logger.getInstance(RefreshQueueImpl.class);

  private final Executor myQueue = AppExecutorUtil.createBoundedApplicationPoolExecutor("RefreshQueue Pool", PooledThreadExecutor.INSTANCE, 1, this);
  private final Executor myEventProcessingQueue =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Async Refresh Event Processing", PooledThreadExecutor.INSTANCE, 1, this);

  private final ProgressIndicator myRefreshIndicator = RefreshProgress.create(VfsBundle.message("file.synchronize.progress"));
  private int myBusyThreads;
  private final TLongObjectHashMap<RefreshSession> mySessions = new TLongObjectHashMap<>();
  private final FrequentEventDetector myEventCounter = new FrequentEventDetector(100, 100, FrequentEventDetector.Level.WARN);

  public void execute(@NotNull RefreshSessionImpl session) {
    if (session.isAsynchronous()) {
      queueSession(session, session.getModality());
    }
    else {
      Application app = ApplicationManager.getApplication();
      if (app.isDispatchThread()) {
        ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
        doScan(session);
        session.fireEvents(session.getEvents(), null);
      }
      else {
        if (((ApplicationEx)app).holdsReadLock()) {
          LOG.error("Do not call synchronous refresh under read lock (except from EDT) - " +
                    "this will cause a deadlock if there are any events to fire.");
          return;
        }
        queueSession(session, ModalityState.defaultModalityState());
        session.waitFor();
      }
    }
  }

  private void queueSession(@NotNull RefreshSessionImpl session, @NotNull ModalityState modality) {
    myQueue.execute(() -> {
      startRefreshActivity();
      try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Doing file refresh. " + session)) {
        doScan(session);
      }
      finally {
        finishRefreshActivity();
        if (Registry.is("vfs.async.event.processing")) {
          scheduleAsynchronousPreprocessing(session, modality);
        }
        else {
          ApplicationManager.getApplication().invokeLater(() -> session.fireEvents(session.getEvents(), null), modality);
        }
      }
    });
    myEventCounter.eventHappened(session);
  }

  private void scheduleAsynchronousPreprocessing(@NotNull RefreshSessionImpl session, @NotNull ModalityState modality) {
    try {
      startRefreshActivity();
      ReadAction
        .nonBlocking(() -> runAsyncListeners(session))
        .cancelWith(myRefreshIndicator)
        .finishOnUiThread(modality, Runnable::run)
        .submit(myEventProcessingQueue)
      .onProcessed(__ -> {
        finishRefreshActivity();
      });
    }
    catch (RejectedExecutionException e) {
      LOG.debug(e);
    }
  }

  private synchronized void startRefreshActivity() {
    if (myBusyThreads++ == 0) {
      myRefreshIndicator.start();
    }
  }

  private synchronized void finishRefreshActivity() {
    if (--myBusyThreads == 0) {
      myRefreshIndicator.stop();
    }
  }

  private static Runnable runAsyncListeners(@NotNull RefreshSessionImpl session) {
    List<? extends VFileEvent> events = ContainerUtil.filter(session.getEvents(), e -> {
      VirtualFile file = e instanceof VFileCreateEvent ? ((VFileCreateEvent)e).getParent() : e.getFile();
      return file == null || file.isValid();
    });

    List<AsyncFileListener.ChangeApplier> appliers = AsyncEventSupport.runAsyncListeners(events);
    return () -> session.fireEvents(events, appliers);
  }

  private void doScan(@NotNull RefreshSessionImpl session) {
    try {
      updateSessionMap(session, true);
      session.scan();
    }
    finally {
      updateSessionMap(session, false);
    }
  }

  private void updateSessionMap(@NotNull RefreshSession session, boolean add) {
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
  public void dispose() {
    synchronized (mySessions) {
      mySessions.forEachValue(session -> { ((RefreshSessionImpl)session).cancel(); return true; });
    }
  }
}