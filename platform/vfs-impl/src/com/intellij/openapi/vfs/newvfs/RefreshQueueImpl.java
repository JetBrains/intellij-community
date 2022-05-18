// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.FrequentEventDetector;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Pair;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static com.intellij.openapi.util.Pair.pair;

public final class RefreshQueueImpl extends RefreshQueue implements Disposable {
  private static final Logger LOG = Logger.getInstance(RefreshQueueImpl.class);

  private final Executor myQueue =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("RefreshQueue Pool", AppExecutorUtil.getAppExecutorService(), 1, this);
  private final Executor myEventProcessingQueue =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("Async Refresh Event Processing", AppExecutorUtil.getAppExecutorService(), 1, this);

  private final ProgressIndicator myRefreshIndicator = RefreshProgress.create(IdeCoreBundle.message("file.synchronize.progress"));
  private final Map<Long, RefreshSessionImpl> mySessions = Collections.synchronizedMap(new HashMap<>());
  private final FrequentEventDetector myEventCounter = new FrequentEventDetector(100, 100, FrequentEventDetector.Level.WARN);
  private int myBusyThreads;

  void execute(@NotNull RefreshSessionImpl session) {
    ApplicationEx app;
    if (session.isAsynchronous()) {
      queueSession(session, session.getModality());
    }
    else if ((app = ApplicationManagerEx.getApplicationEx()).isWriteThread()) {
      ((TransactionGuardImpl)TransactionGuard.getInstance()).assertWriteActionAllowed();
      runRefreshSession(session, -1L);
      fireEvents(session);
    }
    else {
      if (app.holdsReadLock() || EDT.isCurrentThreadEdt()) {
        LOG.error("Do not perform a synchronous refresh under read lock (causes deadlocks if there are events to fire)");
        return;
      }
      queueSession(session, ModalityState.defaultModalityState());
      session.waitFor();
    }
  }

  private void queueSession(RefreshSessionImpl session, ModalityState modality) {
    long queuedAt = System.nanoTime();
    myQueue.execute(() -> {
      long timeInQueue = (System.nanoTime() - queuedAt) / 1_000_000;
      startRefreshActivity();
      try {
        String title = IdeCoreBundle.message("progress.title.doing.file.refresh.0", session);
        HeavyProcessLatch.INSTANCE.performOperation(HeavyProcessLatch.Type.Syncing, title, () -> runRefreshSession(session, timeInQueue));
      }
      finally {
        finishRefreshActivity();
        if (Registry.is("vfs.async.event.processing")) {
          startRefreshActivity();
          ReadAction
            .nonBlocking(() -> runAsyncListeners(session))
            .expireWith(this)
            .wrapProgress(myRefreshIndicator)
            .finishOnUiThread(modality, p -> session.fireEvents(p.first, p.second, true))
            .submit(myEventProcessingQueue)
            .onProcessed(__ -> finishRefreshActivity())
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

  private static void fireEvents(RefreshSessionImpl session) {
    List<CompoundVFileEvent> events = ContainerUtil.map(session.getEvents(), CompoundVFileEvent::new);
    session.fireEvents(events, List.of(), false);
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

  @Override
  public @NotNull RefreshSession createSession(boolean async, boolean recursively, @Nullable Runnable finishRunnable, @NotNull ModalityState state) {
    return new RefreshSessionImpl(async, recursively, finishRunnable, state);
  }

  @Override
  public void processSingleEvent(boolean async, @NotNull VFileEvent event) {
    new RefreshSessionImpl(async, event).launch();
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
    return !(((BoundedTaskExecutor)refreshQueue.myQueue).isEmpty() && refreshQueue.mySessions.isEmpty());
  }

  @TestOnly
  public static void setTestListener(@Nullable Consumer<? super VirtualFile> testListener) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    RefreshWorker.ourTestListener = testListener;
    LocalFileSystemRefreshWorker.setTestListener(testListener);
  }
}
