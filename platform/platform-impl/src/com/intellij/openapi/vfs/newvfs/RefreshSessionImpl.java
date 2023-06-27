// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.codeInsight.daemon.impl.FileStatusMap;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

final class RefreshSessionImpl extends RefreshSession {
  @SuppressWarnings("LoggerInitializedWithForeignClass") private static final Logger LOG = Logger.getInstance(RefreshSession.class);

  private static final int RETRY_LIMIT = SystemProperties.getIntProperty("refresh.session.retry.limit", 3);
  private static final long DURATION_REPORT_THRESHOLD_MS =
    SystemProperties.getIntProperty("refresh.session.duration.report.threshold.seconds", -1) * 1_000L;

  private static final AtomicLong ID_COUNTER = new AtomicLong(0);

  private final long myId = ID_COUNTER.incrementAndGet();
  private final boolean myIsAsync;
  private final boolean myIsRecursive;
  private final Runnable myFinishRunnable;
  private final @Nullable Throwable myStartTrace;
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
    Application app = ApplicationManager.getApplication();
    myStartTrace = app.isUnitTestMode() && (async || !app.isDispatchThread()) ? new Throwable() : null;
  }

  RefreshSessionImpl(boolean async, List<? extends VFileEvent> events) {
    this(async, false, null, getSafeModalityState());
    var filtered = events.stream().filter(Objects::nonNull).toList();
    if (filtered.size() < events.size()) LOG.error("The list of events must not contain null elements");
    myEvents.addAll(filtered);
  }

  private static ModalityState getSafeModalityState() {
    ModalityState state = ModalityState.defaultModalityState();
    return state != ModalityState.any() ? state : ModalityState.nonModal();
  }

  @Override
  public long getId() {
    return myId;
  }

  @Override
  public void addFile(@NotNull VirtualFile file) {
    checkState();
    doAddFile(file);
  }

  @Override
  public void addAllFiles(@NotNull Collection<? extends @NotNull VirtualFile> files) {
    checkState();
    for (VirtualFile file : files) doAddFile(file);
  }

  private void checkState() {
    if (myLaunched) throw new IllegalStateException("Already launched");
  }

  private void doAddFile(VirtualFile file) {
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
    checkState();
    myLaunched = true;
    mySemaphore.down();
    ((RefreshQueueImpl)RefreshQueue.getInstance()).execute(this);
  }

  void scan(long timeInQueue) {
    if (myWorkQueue.isEmpty()) return;
    var workQueue = myWorkQueue;
    myWorkQueue = new ArrayList<>();
    var forceRefresh = !myIsRecursive && !myIsAsync;  // shallow sync refresh (e.g., project config files on open)

    var fs = LocalFileSystem.getInstance();
    if (!forceRefresh && fs instanceof LocalFileSystemImpl) {
      ((LocalFileSystemImpl)fs).markSuspiciousFilesDirty(workQueue);
    }

    if (LOG.isTraceEnabled()) LOG.trace("scanning " + workQueue);

    var t = System.nanoTime();
    PerformanceWatcher.Snapshot snapshot = null;
    Map<String, Integer> types = null;
    if (DURATION_REPORT_THRESHOLD_MS > 0) {
      snapshot = PerformanceWatcher.takeSnapshot();
      types = new HashMap<>();
    }

    var refreshRoots = new ArrayList<NewVirtualFile>(workQueue.size());
    for (var file : workQueue) {
      if (myCancelled) break;

      var nvf = (NewVirtualFile)file;
      if (forceRefresh) {
        nvf.markDirty();
      }
      if (!nvf.isDirty()) {
        continue;
      }
      refreshRoots.add(nvf);

      if (types != null) {
        var type = !file.isDirectory() ? "file" : file.getFileSystem() instanceof ArchiveFileSystem ? "arc" : "dir";
        types.put(type, types.getOrDefault(type, 0) + 1);
      }
    }

    int count = 0;
    do {
      if (LOG.isTraceEnabled()) LOG.trace("try=" + count);

      var worker = new RefreshWorker(refreshRoots, myIsRecursive);
      myWorker = worker;
      myEvents.addAll(worker.scan());
      myWorker = null;

      count++;
      if (LOG.isTraceEnabled()) LOG.trace("events=" + myEvents.size());
    }
    while (!myCancelled && myIsRecursive && count < RETRY_LIMIT && ContainerUtil.exists(workQueue, f -> ((NewVirtualFile)f).isDirty()));

    t = NANOSECONDS.toMillis(System.nanoTime() - t);
    int localRoots = 0, archiveRoots = 0, otherRoots = 0;
    for (var file : refreshRoots) {
      if (file.getFileSystem() instanceof LocalFileSystem) localRoots++;
      else if (file.getFileSystem() instanceof ArchiveFileSystem) archiveRoots++;
      else otherRoots++;
    }
    VfsUsageCollector.logRefreshSession(myIsRecursive, localRoots, archiveRoots, otherRoots, myCancelled, timeInQueue, t, count);
    if (LOG.isTraceEnabled()) {
      LOG.trace((myCancelled ? "cancelled, " : "done, ") + t + " ms, tries " + count + ", events " + myEvents);
    }
    else if (snapshot != null && t > DURATION_REPORT_THRESHOLD_MS) {
      snapshot.logResponsivenessSinceCreation(String.format(
        "Refresh session (queue size: %s, scanned: %s, result: %s, tries: %s, events: %d)",
        workQueue.size(), types, myCancelled ? "cancelled" : "done", count, myEvents.size()));
    }
  }

  void cancel() {
    myCancelled = true;

    RefreshWorker worker = myWorker;
    if (worker != null) {
      worker.cancel();
    }
  }

  void fireEvents(@NotNull List<CompoundVFileEvent> events, @NotNull List<AsyncFileListener.ChangeApplier> appliers, boolean asyncProcessing) {
    try {
      ApplicationEx app = ApplicationManagerEx.getApplicationEx();
      if ((myFinishRunnable != null || !events.isEmpty()) && !app.isDisposed()) {
        if (LOG.isDebugEnabled()) LOG.debug("events are about to fire: " + events);
        WriteAction.run(() -> {
          app.runWriteActionWithNonCancellableProgressInDispatchThread(IdeCoreBundle.message("progress.title.file.system.synchronization"), null, null, indicator -> {
            indicator.setText(IdeCoreBundle.message("progress.text.processing.detected.file.changes", events.size()));
            int progressThresholdMillis = 5_000;
            ((ProgressIndicatorWithDelayedPresentation)indicator).setDelayInMillis(progressThresholdMillis);
            long t = System.nanoTime();
            fireEventsInWriteAction(events, appliers, asyncProcessing);
            t = NANOSECONDS.toMillis(System.nanoTime() - t);
            if (t > progressThresholdMillis) {
              LOG.warn("Long VFS change processing (" + t + "ms, " + events.size() + " events): " + StringUtil.trimLog(events.toString(), 10_000));
            }
          });
        });
      }
    }
    finally {
      mySemaphore.up();
    }
  }

  private void fireEventsInWriteAction(List<CompoundVFileEvent> events, List<AsyncFileListener.ChangeApplier> appliers, boolean asyncProcessing) {
    VirtualFileManagerEx manager = (VirtualFileManagerEx)VirtualFileManager.getInstance();

    manager.fireBeforeRefreshStart(myIsAsync);
    try {
      AsyncEventSupport.processEventsFromRefresh(events, appliers, asyncProcessing);
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

  Semaphore getSemaphore() {
    return mySemaphore;
  }

  @NotNull ModalityState getModality() {
    return myModality;
  }

  boolean hasEvents() {
    return !myEvents.isEmpty();
  }

  @NotNull List<VFileEvent> getEvents() {
    return hasEvents() ? new ArrayList<>(new LinkedHashSet<>(myEvents)) : List.of();
  }

  @Override
  public String toString() {
    int size = myWorkQueue.size();
    return "RefreshSessionImpl: " + size + " root(s) in the queue" +
           (size == 0 ? "" : ": " + ContainerUtil.getFirstItem(myWorkQueue)) + (size >= 2 ? ", ..." : "");
  }
}
