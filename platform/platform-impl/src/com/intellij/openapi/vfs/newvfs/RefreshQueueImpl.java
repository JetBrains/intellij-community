// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.CoroutineDispatcherBackedExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import com.intellij.util.ui.EDT;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.util.concurrency.AppJavaExecutorUtil.createSingleTaskApplicationPoolExecutor;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class RefreshQueueImpl extends RefreshQueue implements Disposable {
  private static final Logger LOG = Logger.getInstance(RefreshQueueImpl.class);

  private final Executor myQueue;
  private final Executor myEventProcessingQueue;

  private final ProgressIndicator myRefreshIndicator = RefreshProgress.create();
  private final Map<Long, RefreshSessionImpl> mySessions = Collections.synchronizedMap(new HashMap<>());
  private final FrequentEventDetector myEventCounter = new FrequentEventDetector(100, 100, FrequentEventDetector.Level.WARN);
  private int myActivityCounter;

  public RefreshQueueImpl(@NotNull CoroutineScope coroutineScope) {
    myQueue = createSingleTaskApplicationPoolExecutor("RefreshQueue Pool", coroutineScope);
    myEventProcessingQueue = createSingleTaskApplicationPoolExecutor("Async Refresh Event Processing", coroutineScope);
  }

  void execute(@NotNull RefreshSessionImpl session) {
    ApplicationEx app;
    if (session.isAsynchronous()) {
      queueSession(session, session.getModality());
    }
    else if ((app = ApplicationManagerEx.getApplicationEx()).isWriteIntentLockAcquired()) {
      ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
      runRefreshSession(session, -1L);
      fireEvents(session);
    }
    else if (app.holdsReadLock() || EDT.isCurrentThreadEdt()) {
      LOG.error("Do not perform a synchronous refresh under read lock (causes deadlocks if there are events to fire). " +
                "EDT=" + EDT.isCurrentThreadEdt());
    }
    else {
      queueSession(session, session.getModality());
      session.waitFor();
    }
  }

  private void queueSession(RefreshSessionImpl session, ModalityState modality) {
    var queuedAt = System.nanoTime();
    myQueue.execute(() -> {
      long timeInQueue = NANOSECONDS.toMillis(System.nanoTime() - queuedAt);
      startIndicator(IdeCoreBundle.message("file.synchronize.progress"));
      try {
        String title = IdeCoreBundle.message("progress.title.doing.file.refresh.0", session);
        HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Syncing, title, () -> runRefreshSession(session, timeInQueue));
      }
      finally {
        stopIndicator();
        if (Registry.is("vfs.async.event.processing", true) && session.hasEvents()) {
          var evQueuedAt = System.nanoTime();
          var evTimeInQueue = new AtomicLong(-1);
          var evListenerTime = new AtomicLong(-1);
          var evRetries = new AtomicLong(0);
          startIndicator(IdeCoreBundle.message("async.events.progress"));
          ReadAction
            .nonBlocking(() -> {
              evTimeInQueue.compareAndSet(-1, NANOSECONDS.toMillis(System.nanoTime() - evQueuedAt));
              evRetries.incrementAndGet();
              var t = System.nanoTime();
              try {
                return runAsyncListeners(session);
              }
              finally {
                evListenerTime.addAndGet(System.nanoTime() - t);
              }
            })
            .expireWith(this)
            .wrapProgress(myRefreshIndicator)
            .finishOnUiThread(modality, data -> {
              var t = System.nanoTime();
              session.fireEvents(data.first, data.second, true);
              t = NANOSECONDS.toMillis(System.nanoTime() - t);
              VfsUsageCollector.logEventProcessing(
                evTimeInQueue.longValue(), NANOSECONDS.toMillis(evListenerTime.longValue()), evRetries.intValue(), t, data.second.size());
            })
            .submit(myEventProcessingQueue)
            .onProcessed(__ -> stopIndicator())
            .onError(t -> {
              if (!myRefreshIndicator.isCanceled()) {
                LOG.error(t);
              }
            });
        }
        else {
          AppUIExecutor.onWriteThread(modality).later().submit(() -> fireEvents(session));
        }
      }
    });
    myEventCounter.eventHappened(session);
  }

  private synchronized void startIndicator(@NlsContexts.ProgressText String text) {
    if (myActivityCounter++ == 0) {
      myRefreshIndicator.setText(text);
      myRefreshIndicator.start();
    }
  }

  private synchronized void stopIndicator() {
    if (--myActivityCounter == 0) {
      myRefreshIndicator.stop();
    }
  }

  private static void fireEvents(RefreshSessionImpl session) {
    var t = System.nanoTime();
    List<CompoundVFileEvent> events = ContainerUtil.map(session.getEvents(), CompoundVFileEvent::new);
    session.fireEvents(events, List.of(), false);
    t = NANOSECONDS.toMillis(System.nanoTime() - t);
    VfsUsageCollector.logEventProcessing(-1L, -1L, -1, t, events.size());
  }

  private static Pair<List<CompoundVFileEvent>, List<AsyncFileListener.ChangeApplier>> runAsyncListeners(RefreshSessionImpl session) {
    List<CompoundVFileEvent> events = ContainerUtil.mapNotNull(session.getEvents(), e -> {
      VirtualFile file = e instanceof VFileCreateEvent ? ((VFileCreateEvent)e).getParent() : e.getFile();
      return file == null || file.isValid() ? new CompoundVFileEvent(e) : null;
    });
    List<VFileEvent> allEvents = ContainerUtil.flatMap(events, e -> {
      List<VFileEvent> toMap = new SmartList<>(e.getInducedEvents());
      toMap.add(e.getFileEvent());
      return toMap;
    });
    List<AsyncFileListener.ChangeApplier> appliers = AsyncEventSupport.runAsyncListeners(allEvents);
    return pair(events, appliers);
  }

  private void runRefreshSession(RefreshSessionImpl session, long timeInQueue) {
    try {
      mySessions.put(session.getId(), session);
      session.scan(timeInQueue);
    }
    finally {
      mySessions.remove(session.getId());
    }
  }

  @Override
  public void cancelSession(long id) {
    RefreshSessionImpl session = mySessions.get(id);
    if (session != null) {
      session.cancel();
    }
  }

  RefreshSessionImpl getSession(long id) {
    return mySessions.get(id);
  }

  @Override
  public @NotNull RefreshSession createSession(boolean async, boolean recursively, @Nullable Runnable finishRunnable, @NotNull ModalityState state) {
    return new RefreshSessionImpl(async, recursively, finishRunnable, state);
  }

  @Override
  public void processEvents(boolean async, @NotNull List<? extends @NotNull VFileEvent> events) {
    new RefreshSessionImpl(async, events).launch();
  }

  @Override
  public void dispose() {
    Collection<RefreshSessionImpl> sessions = mySessions.values();
    synchronized (mySessions) {
      for (RefreshSessionImpl session : sessions) {
        session.cancel();
      }
    }
  }

  public static boolean isRefreshInProgress() {
    RefreshQueueImpl refreshQueue = (RefreshQueueImpl)RefreshQueue.getInstance();
    return !refreshQueue.mySessions.isEmpty() || !((CoroutineDispatcherBackedExecutor)refreshQueue.myQueue).isEmpty();
  }

  @TestOnly
  public static void setTestListener(@Nullable Consumer<? super VirtualFile> testListener) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    RefreshWorker.ourTestListener = testListener;
  }
}
