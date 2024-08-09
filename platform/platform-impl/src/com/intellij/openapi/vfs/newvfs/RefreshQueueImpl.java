// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static com.intellij.util.concurrency.AppJavaExecutorUtil.createBoundedTaskExecutor;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public final class RefreshQueueImpl extends RefreshQueue implements Disposable {
  @SuppressWarnings("LoggerInitializedWithForeignClass") private static final Logger LOG = Logger.getInstance(RefreshQueue.class);

  private final CoroutineDispatcherBackedExecutor myQueue;
  private final CoroutineDispatcherBackedExecutor myEventProcessingQueue;

  private final ProgressIndicator myRefreshIndicator = RefreshProgress.create();
  private final Set<RefreshSessionImpl> mySessions = Collections.synchronizedSet(new HashSet<>());
  private final FrequentEventDetector myEventCounter = new FrequentEventDetector(100, 100, FrequentEventDetector.Level.WARN);
  private int myActivityCounter;

  public RefreshQueueImpl(@NotNull CoroutineScope coroutineScope) {
    myQueue = createBoundedTaskExecutor("RefreshQueue Pool", coroutineScope);
    myEventProcessingQueue = createBoundedTaskExecutor("Async Refresh Event Processing", coroutineScope);
  }

  void execute(@NotNull RefreshSessionImpl session) {
    ApplicationEx app;
    if (session.isAsynchronous()) {
      queueSession(session, session.getModality());
    }
    else if ((app = ApplicationManagerEx.getApplicationEx()).isWriteIntentLockAcquired()) {
      ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
      var events = runRefreshSession(session, -1L);
      fireEvents(events, session);
    }
    else if (app.holdsReadLock()) {
      LOG.error("Do not perform a synchronous refresh under read lock (causes deadlocks if there are events to fire)");
    }
    else if (EDT.isCurrentThreadEdt()) {
      LOG.error("Do not perform a synchronous refresh on naked EDT (without WIL) (causes deadlocks if there are events to fire)");
    }
    else {
      queueSession(session, session.getModality());
      session.waitFor();
    }
  }

  private void queueSession(RefreshSessionImpl session, ModalityState modality) {
    if (LOG.isDebugEnabled()) LOG.debug("Queue session with id=" + session.hashCode());
    if (session.isEventSession() && !session.isAsynchronous()) {
      processEvents(session, session.getModality(), runRefreshSession(session, -1L));
    }
    else {
      var queuedAt = System.nanoTime();
      myQueue.execute(() -> {
        var timeInQueue = NANOSECONDS.toMillis(System.nanoTime() - queuedAt);
        startIndicator(IdeCoreBundle.message("file.synchronize.progress"));
        var events = new AtomicReference<Collection<VFileEvent>>();
        try {
          var title = IdeCoreBundle.message("progress.title.doing.file.refresh.0", session);
          HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Syncing, title, () -> events.set(runRefreshSession(session, timeInQueue)));
        }
        finally {
          stopIndicator();
          processEvents(session, modality, events.get());
        }
      });
    }
    myEventCounter.eventHappened(session);
  }

  private void processEvents(RefreshSessionImpl session, ModalityState modality, Collection<VFileEvent> events) {
    if (Registry.is("vfs.async.event.processing", true) && !events.isEmpty()) {
      var evQueuedAt = System.nanoTime();
      var evTimeInQueue = new AtomicLong(-1);
      var evListenerTime = new AtomicLong(-1);
      var evRetries = new AtomicLong(0);
      startIndicator(IdeCoreBundle.message("async.events.progress"));
      ReadAction
        .nonBlocking(() -> {
          if (LOG.isDebugEnabled()) LOG.debug("Start non-blocking action for session with id=" + session.hashCode());
          evTimeInQueue.compareAndSet(-1, NANOSECONDS.toMillis(System.nanoTime() - evQueuedAt));
          evRetries.incrementAndGet();
          var t = System.nanoTime();
          try {
            var result = runAsyncListeners(events);
            if (LOG.isDebugEnabled()) LOG.debug("Successful finish of non-blocking read action for session with id=" + session.hashCode());
            return result;
          }
          finally {
            if (LOG.isDebugEnabled()) LOG.debug("Final block of non-blocking read action for  session with id=" + session.hashCode());
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
      //noinspection deprecation
      AppUIExecutor.onWriteThread(modality).later().submit(() -> fireEvents(events, session));
    }
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

  private static void fireEvents(Collection<VFileEvent> events, RefreshSessionImpl session) {
    var t = System.nanoTime();
    var compoundEvents = ContainerUtil.map(events, CompoundVFileEvent::new);
    session.fireEvents(compoundEvents, List.of(), false);
    t = NANOSECONDS.toMillis(System.nanoTime() - t);
    VfsUsageCollector.logEventProcessing(-1L, -1L, -1, t, compoundEvents.size());
  }

  private static Pair<List<CompoundVFileEvent>, List<AsyncFileListener.ChangeApplier>> runAsyncListeners(Collection<VFileEvent> events) {
    var compoundEvents = ContainerUtil.mapNotNull(events, e -> {
      var file = e instanceof VFileCreateEvent ? ((VFileCreateEvent)e).getParent() : e.getFile();
      return file == null || file.isValid() ? new CompoundVFileEvent(e) : null;
    });
    var allEvents = ContainerUtil.flatMap(compoundEvents, e -> {
      var toMap = new SmartList<>(e.getInducedEvents());
      toMap.add(e.getFileEvent());
      return toMap;
    });
    return new Pair<>(compoundEvents, AsyncEventSupport.runAsyncListeners(allEvents));
  }

  private Collection<VFileEvent> runRefreshSession(RefreshSessionImpl session, long timeInQueue) {
    try {
      mySessions.add(session);
      return session.scan(timeInQueue);
    }
    finally {
      mySessions.remove(session);
    }
  }

  @Override
  public @NotNull RefreshSession createSession(boolean async, boolean recursively, @Nullable Runnable finishRunnable, @NotNull ModalityState state) {
    return new RefreshSessionImpl(async, recursively, false, finishRunnable, state);
  }

  @Override
  public void processEvents(boolean async, @NotNull List<? extends @NotNull VFileEvent> events) {
    new RefreshSessionImpl(async, events).launch();
  }

  @ApiStatus.Internal
  public @NotNull RefreshSession createBackgroundRefreshSession(@NotNull List<@NotNull VirtualFile> files) {
    return new RefreshSessionImpl(files);
  }

  @Override
  public void dispose() {
    synchronized (mySessions) {
      for (var session : mySessions) {
        session.cancel();
      }
    }
    myEventProcessingQueue.cancel();
    myQueue.cancel();
  }

  public static boolean isRefreshInProgress() {
    var refreshQueue = (RefreshQueueImpl)getInstance();
    return !refreshQueue.mySessions.isEmpty() || !refreshQueue.myQueue.isEmpty();
  }

  @ApiStatus.Internal
  @TestOnly
  public static boolean isEventProcessingInProgress() {
    var refreshQueue = (RefreshQueueImpl)getInstance();
    return !refreshQueue.myEventProcessingQueue.isEmpty();
  }

  @TestOnly
  public static void setTestListener(@Nullable Consumer<? super VirtualFile> testListener) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    RefreshWorker.ourTestListener = testListener;
  }
}
