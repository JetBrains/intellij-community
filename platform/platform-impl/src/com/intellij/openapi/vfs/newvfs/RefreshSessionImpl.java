// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.codeInsight.daemon.impl.FileStatusMap;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

final class RefreshSessionImpl extends RefreshSession {
  private static final Logger LOG = Logger.getInstance(RefreshSession.class);

  private static final int RETRY_LIMIT = SystemProperties.getIntProperty("refresh.session.retry.limit", 3);
  private static final long DURATION_REPORT_THRESHOLD_MS =
    SystemProperties.getIntProperty("refresh.session.duration.report.threshold.seconds", -1) * 1_000L;

  private static final AtomicLong ID_COUNTER = new AtomicLong(0);

  private final long myId = ID_COUNTER.incrementAndGet();
  private final boolean myIsAsync;
  private final boolean myIsRecursive;
  private final Runnable myFinishRunnable;
  private final Throwable myStartTrace;
  private final Semaphore mySemaphore = new Semaphore();

  private List<VirtualFile> myWorkQueue = new ArrayList<>();
  private final List<VFileEvent> myEvents = new ArrayList<>();
  private volatile RefreshWorker myWorker;
  private volatile boolean myCancelled;
  private final ModalityState myModality;
  private boolean myLaunched;

  RefreshSessionImpl(boolean async, boolean recursive, @Nullable Runnable finishRunnable, @NotNull ModalityState modality) {
    myIsAsync = async;
    myIsRecursive = recursive;
    myFinishRunnable = finishRunnable;
    myModality = modality;
    TransactionGuard.getInstance().assertWriteSafeContext(modality);
    myStartTrace = rememberStartTrace();
  }

  private Throwable rememberStartTrace() {
    boolean trace = ApplicationManager.getApplication().isUnitTestMode() && (myIsAsync || !ApplicationManager.getApplication().isDispatchThread());
    return trace ? new Throwable() : null;
  }

  RefreshSessionImpl(boolean async, @NotNull List<? extends VFileEvent> events) {
    this(async, false, null, ModalityState.defaultModalityState());
    myEvents.addAll(events);
  }

  @Override
  public long getId() {
    return myId;
  }

  @Override
  public void addAllFiles(@NotNull Collection<? extends VirtualFile> files) {
    for (VirtualFile file : files) {
      if (file == null) {
        LOG.error("null passed among " + files);
      }
      else {
        addFile(file);
      }
    }
  }

  @Override
  public void addFile(@NotNull VirtualFile file) {
    if (myLaunched) {
      throw new IllegalStateException("Adding files is only allowed before launch");
    }
    if (file instanceof NewVirtualFile) {
      myWorkQueue.add(file);
    }
    else {
      LOG.debug("skipped: " + file + " / " + file.getClass());
    }
  }

  @Override
  public boolean isAsynchronous() {
    return myIsAsync;
  }

  @Override
  public void launch() {
    if (myLaunched) {
      throw new IllegalStateException("launch() can be called only once");
    }
    myLaunched = true;
    mySemaphore.down();
    ((RefreshQueueImpl)RefreshQueue.getInstance()).execute(this);
  }

  void scan() {
    List<VirtualFile> workQueue = myWorkQueue;
    myWorkQueue = new ArrayList<>();
    boolean forceRefresh = !myIsRecursive && !myIsAsync;  // shallow sync refresh (e.g. project config files on open)

    if (!workQueue.isEmpty()) {
      LocalFileSystem fs = LocalFileSystem.getInstance();
      if (!forceRefresh && fs instanceof LocalFileSystemImpl) {
        ((LocalFileSystemImpl)fs).markSuspiciousFilesDirty(workQueue);
      }

      if (LOG.isTraceEnabled()) LOG.trace("scanning " + workQueue);

      long t = System.currentTimeMillis();
      PerformanceWatcher.Snapshot snapshot = null;
      Map<String, Integer> types = null;
      if (DURATION_REPORT_THRESHOLD_MS > 0) {
        snapshot = PerformanceWatcher.takeSnapshot();
        types = new HashMap<>();
      }

      int count = 0;
      refresh: do {
        if (LOG.isTraceEnabled()) LOG.trace("try=" + count);

        for (VirtualFile file : workQueue) {
          if (myCancelled) break refresh;

          NewVirtualFile nvf = (NewVirtualFile)file;
          if (forceRefresh) {
            nvf.markDirty();
          }
          else if (!nvf.isDirty()) {
            continue;
          }

          RefreshWorker worker = new RefreshWorker(nvf, myIsRecursive);
          myWorker = worker;
          worker.scan();
          myEvents.addAll(worker.getEvents());

          if (types != null) {
            String type = !file.isDirectory() ? "file" : file.getFileSystem() instanceof ArchiveFileSystem ? "arc" : "dir";
            types.put(type, types.getOrDefault(type, 0) + 1);
          }
        }

        count++;
        if (LOG.isTraceEnabled()) LOG.trace("events=" + myEvents.size());
      }
      while (!myCancelled && myIsRecursive && count < RETRY_LIMIT && ContainerUtil.exists(workQueue, f -> ((NewVirtualFile)f).isDirty()));

      t = System.currentTimeMillis() - t;
      if (LOG.isTraceEnabled()) {
        LOG.trace((myCancelled ? "cancelled, " : "done, ") + t + " ms, tries " + count + ", events " + myEvents);
      }
      else if (snapshot != null && t > DURATION_REPORT_THRESHOLD_MS) {
        snapshot.logResponsivenessSinceCreation(String.format(
          "Refresh session (queue size: %s, scanned: %s, result: %s, tries: %s, events: %d)",
          workQueue.size(), types, myCancelled ? "cancelled" : "done", count, myEvents.size()));
      }
    }

    myWorker = null;
  }

  void cancel() {
    myCancelled = true;

    RefreshWorker worker = myWorker;
    if (worker != null) {
      worker.cancel();
    }
  }

  void fireEvents(@NotNull List<? extends CompoundVFileEvent> events, @NotNull List<? extends AsyncFileListener.ChangeApplier> appliers, boolean asyncEventProcessing) {
    try {
      ApplicationImpl app = (ApplicationImpl)ApplicationManager.getApplication();
      if ((myFinishRunnable != null || !events.isEmpty()) && !app.isDisposed()) {
        if (LOG.isDebugEnabled()) LOG.debug("events are about to fire: " + events);
        WriteAction.run(() -> {
          app.runWriteActionWithNonCancellableProgressInDispatchThread(IdeBundle.message("progress.title.file.system.synchronization"), null, null, indicator -> {
            indicator.setText(IdeBundle.message("progress.text.processing.detected.file.changes", events.size()));
            int progressThresholdMillis = 5_000;
            ((ProgressWindow) indicator).setDelayInMillis(progressThresholdMillis);
            long start = System.currentTimeMillis();

            fireEventsInWriteAction(events, appliers, asyncEventProcessing);

            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > progressThresholdMillis) {
              LOG.warn("Long VFS change processing (" + elapsed + "ms, " + events.size() + " events): " +
                       StringUtil.trimLog(events.toString(), 10_000));
            }
          });
        });
      }
    }
    finally {
      mySemaphore.up();
    }
  }

  private void fireEventsInWriteAction(@NotNull List<? extends CompoundVFileEvent> events,
                                       @NotNull List<? extends AsyncFileListener.ChangeApplier> appliers, boolean asyncEventProcessing) {
    final VirtualFileManagerEx manager = (VirtualFileManagerEx)VirtualFileManager.getInstance();

    manager.fireBeforeRefreshStart(myIsAsync);
    try {
      AsyncEventSupport.processEventsFromRefresh(events, appliers, asyncEventProcessing);
    }
    catch (AssertionError e) {
      if (FileStatusMap.CHANGES_NOT_ALLOWED_DURING_HIGHLIGHTING.equals(e.getMessage())) {
        throw new AssertionError("VFS changes are not allowed during highlighting", myStartTrace);
      }
      throw e;
    }
    finally {
      try {
        manager.fireAfterRefreshFinish(myIsAsync);
      }
      finally {
        if (myFinishRunnable != null) {
          myFinishRunnable.run();
        }
      }
    }
  }

  void waitFor() {
    mySemaphore.waitFor();
  }

  @NotNull
  ModalityState getModality() {
    return myModality;
  }

  @NotNull
  List<? extends VFileEvent> getEvents() {
    return new ArrayList<>(new LinkedHashSet<>(myEvents));
  }

  @Override
  public String toString() {
    return myWorkQueue.size() <= 1 ? "" : myWorkQueue.size() + " roots in the queue.";
  }
}
