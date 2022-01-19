// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.ide.IdeCoreBundle;
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
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.EDT;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class RefreshQueueImpl extends RefreshQueue implements Disposable {
  private static final Logger LOG = Logger.getInstance(RefreshQueueImpl.class);

  private final Executor myQueue = AppExecutorUtil.createBoundedApplicationPoolExecutor("RefreshQueue Pool", AppExecutorUtil.getAppExecutorService(), 1, this);
  private final Executor myEventProcessingQueue =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Async Refresh Event Processing", AppExecutorUtil.getAppExecutorService(), 1, this);

  private final ProgressIndicator myRefreshIndicator = RefreshProgress.create(IdeCoreBundle.message("file.synchronize.progress"));
  private int myBusyThreads;
  private final Long2ObjectMap<RefreshSessionImpl> mySessions = new Long2ObjectOpenHashMap<>();
  private final FrequentEventDetector myEventCounter = new FrequentEventDetector(100, 100, FrequentEventDetector.Level.WARN);

  void execute(@NotNull RefreshSessionImpl session) {
    if (session.isAsynchronous()) {
      queueSessionAsync(session, session.getModality());
    }
    else {
      Application app = ApplicationManager.getApplication();
      if (app.isWriteThread()) {
        queueSessionSync(session);
      }
      else {
        if (((ApplicationEx)app).holdsReadLock() || EDT.isCurrentThreadEdt()) {
          LOG.error("Do not perform a synchronous refresh under read lock (except from EDT) - causes deadlocks if there are events to fire.");
          return;
        }
        queueSessionAsync(session, ModalityState.defaultModalityState());
        session.waitFor();
      }
    }
  }

  private void queueSessionSync(@NotNull RefreshSessionImpl session) {
    ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
    executeRefreshSession(session);
    fireEventsSync(session);
  }

  private static void fireEventsSync(@NotNull RefreshSessionImpl session) {
    session.fireEvents(ContainerUtil.map(session.getEvents(), e -> new CompoundVFileEvent(e)), Collections.emptyList(), false);
  }

  private void queueSessionAsync(@NotNull RefreshSessionImpl session, @NotNull ModalityState modality) {
    myQueue.execute(() -> executeSession(session, modality));
    myEventCounter.eventHappened(session);
  }

  private void executeSession(@NotNull RefreshSessionImpl session, @NotNull ModalityState modality) {
    startRefreshActivity();
    try {
      HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Syncing, IdeCoreBundle.message("progress.title.doing.file.refresh.0", session), ()-> executeRefreshSession(session));
    }
    finally {
      finishRefreshActivity();
      fireEventsAsync(session, modality);
    }
  }

  private void fireEventsAsync(@NotNull RefreshSessionImpl session, @NotNull ModalityState modality) {
    if (isAsyncEventProcessingEnabled()) {
      scheduleAsynchronousPreprocessing(session, modality);
    }
    else {
      AppUIExecutor.onWriteThread(modality).later().submit(() -> fireEventsSync(session));
    }
  }

  private static boolean isAsyncEventProcessingEnabled() {
    return Registry.is("vfs.async.event.processing");
  }

  private void scheduleAsynchronousPreprocessing(@NotNull RefreshSessionImpl session, @NotNull ModalityState modality) {
    startRefreshActivity();
    ReadAction
      .nonBlocking(() -> runAsyncListeners(session))
      .expireWith(this)
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
    List<CompoundVFileEvent> events = ContainerUtil.mapNotNull(session.getEvents(), e -> {
      VirtualFile file = e instanceof VFileCreateEvent ? ((VFileCreateEvent)e).getParent() : e.getFile();
      if (file == null || file.isValid()) {
        return new CompoundVFileEvent(e);
      }
      return null;
    });

    List<VFileEvent> allEvents = ContainerUtil.flatMap(events, e -> {
      List<VFileEvent> toMap = new SmartList<>(e.getInducedEvents());
      toMap.add(e.getFileEvent());
      return toMap;
    });

    List<AsyncFileListener.ChangeApplier> appliers = AsyncEventSupport.runAsyncListeners(allEvents);
    return () -> session.fireEvents(events, appliers, true);
  }

  private void executeRefreshSession(@NotNull RefreshSessionImpl session) {
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
  public void processSingleEvent(boolean async, @NotNull VFileEvent event) {
    new RefreshSessionImpl(async, Collections.singletonList(event)).launch();
  }

  public static boolean isRefreshInProgress() {
    RefreshQueueImpl refreshQueue = (RefreshQueueImpl)RefreshQueue.getInstance();
    if (!((BoundedTaskExecutor)refreshQueue.myQueue).isEmpty()) {
      return true;
    }
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

  @TestOnly
  public static void setTestListener(@Nullable Consumer<? super VirtualFile> testListener) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    RefreshWorker.ourTestListener = testListener;
    LocalFileSystemRefreshWorker.setTestListener(testListener);
  }
}
