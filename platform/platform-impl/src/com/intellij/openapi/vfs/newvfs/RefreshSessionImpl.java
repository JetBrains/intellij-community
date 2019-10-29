// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.codeInsight.daemon.impl.FileStatusMap;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author max
 */
class RefreshSessionImpl extends RefreshSession {
  private static final Logger LOG = Logger.getInstance(RefreshSession.class);

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
    LOG.assertTrue(modality == ModalityState.NON_MODAL || modality != ModalityState.any(), "Refresh session should have a specific modality");
    myStartTrace = rememberStartTrace();
  }

  private Throwable rememberStartTrace() {
    if (ApplicationManager.getApplication().isUnitTestMode() &&
        (myIsAsync || !ApplicationManager.getApplication().isDispatchThread())) {
      return new Throwable();
    }
    return null;
  }

  RefreshSessionImpl(@NotNull List<? extends VFileEvent> events) {
    this(false, false, null, ModalityState.defaultModalityState());
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

      long t = 0;
      if (LOG.isTraceEnabled()) {
        LOG.trace("scanning " + workQueue);
        t = System.currentTimeMillis();
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
        }

        count++;
        if (LOG.isTraceEnabled()) LOG.trace("events=" + myEvents.size());
      }
      while (!myCancelled && myIsRecursive && count < 3 && ContainerUtil.exists(workQueue, f -> ((NewVirtualFile)f).isDirty()));

      if (t != 0) {
        t = System.currentTimeMillis() - t;
        LOG.trace((myCancelled ? "cancelled, " : "done, ") + t + " ms, events " + myEvents);
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

  void fireEvents(@NotNull List<? extends VFileEvent> events, @Nullable List<? extends AsyncFileListener.ChangeApplier> appliers) {
    try {
      if ((myFinishRunnable != null || !events.isEmpty()) && !ApplicationManager.getApplication().isDisposed()) {
        if (LOG.isDebugEnabled()) LOG.debug("events are about to fire: " + events);
        WriteAction.run(() -> fireEventsInWriteAction(events, appliers));
      }
    }
    finally {
      mySemaphore.up();
    }
  }

  private void fireEventsInWriteAction(List<? extends VFileEvent> events, @Nullable List<? extends AsyncFileListener.ChangeApplier> appliers) {
    final VirtualFileManagerEx manager = (VirtualFileManagerEx)VirtualFileManager.getInstance();

    manager.fireBeforeRefreshStart(myIsAsync);
    try {
      AsyncEventSupport.processEvents(events, appliers);
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

  @Override
  public String toString() {
    return myWorkQueue.size() <= 1 ? "" : myWorkQueue.size() + " roots in queue.";
  }

  @NotNull
  List<? extends VFileEvent> getEvents() {
    return new ArrayList<>(new LinkedHashSet<>(myEvents));
  }
}