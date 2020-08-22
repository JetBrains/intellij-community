// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.EDT;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

public final class RefreshQueueImpl extends RefreshQueue implements Disposable {
  private static final Logger LOG = Logger.getInstance(RefreshQueueImpl.class);

  private final Executor myQueue = AppExecutorUtil.createBoundedApplicationPoolExecutor("RefreshQueue Pool", AppExecutorUtil.getAppExecutorService(), 1, this);
  private final Executor myEventProcessingQueue =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Async Refresh Event Processing", AppExecutorUtil.getAppExecutorService(), 1, this);

  private final ProgressIndicator myRefreshIndicator = RefreshProgress.create(IdeBundle.message("file.synchronize.progress"));
  private int myBusyThreads;
  private final Long2ObjectOpenHashMap<RefreshSessionImpl> mySessions = new Long2ObjectOpenHashMap<>();
  private final FrequentEventDetector myEventCounter = new FrequentEventDetector(100, 100, FrequentEventDetector.Level.WARN);

  public void execute(@NotNull RefreshSessionImpl session) {
    if (session.isAsynchronous()) {
      queueSession(session, session.getModality());
    }
    else {
      Application app = ApplicationManager.getApplication();
      if (app.isWriteThread()) {
        ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
        doScan(session);
        session.fireEvents(session.getEvents(), null);
      }
      else {
        if (((ApplicationEx)app).holdsReadLock() || EDT.isCurrentThreadEdt()) {
          LOG.error("Do not perform a synchronous refresh under read lock (except from EDT) - causes deadlocks if there are events to fire.");
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
      try (AccessToken ignored = HeavyProcessLatch.INSTANCE.processStarted("Doing file refresh. " + session, HeavyProcessLatch.Type.Syncing)) {
        doScan(session);
      }
      finally {
        finishRefreshActivity();
        if (Registry.is("vfs.async.event.processing")) {
          scheduleAsynchronousPreprocessing(session, modality);
        }
        else {
          AppUIExecutor.onWriteThread(modality).later().submit(() -> session.fireEvents(session.getEvents(), null));
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
        .wrapProgress(myRefreshIndicator)
        .finishOnUiThread(modality, Runnable::run)
        .submit(myEventProcessingQueue)
        .onProcessed(__ -> finishRefreshActivity())
        .onError(t -> {
          if (!myRefreshIndicator.isCanceled()) {
            LOG.error(t);
          }
        });
    }
    catch (RejectedExecutionException | AlreadyDisposedException e) {
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

  private static @NotNull Runnable runAsyncListeners(@NotNull RefreshSessionImpl session) {
    List<VFileEvent> events = ContainerUtil.filter(session.getEvents(), e -> {
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

  private void updateSessionMap(@NotNull RefreshSessionImpl session, boolean add) {
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
    RefreshSessionImpl session;
    synchronized (mySessions) {
      session = mySessions.get(id);
    }
    if (session != null) {
      session.cancel();
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
      for (RefreshSessionImpl session : mySessions.values()) {
        session.cancel();
      }
    }
  }
}