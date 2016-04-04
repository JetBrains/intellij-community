/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.impl.local.FileWatcher;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author max
 */
public class RefreshSessionImpl extends RefreshSession {
  private static final Logger LOG = Logger.getInstance(RefreshSession.class);

  private static final AtomicLong ID_COUNTER = new AtomicLong(0);

  private final long myId = ID_COUNTER.incrementAndGet();
  private final boolean myIsAsync;
  private final boolean myIsRecursive;
  private final Runnable myFinishRunnable;
  private final ModalityState myModalityState;
  private final Semaphore mySemaphore = new Semaphore();

  private List<VirtualFile> myWorkQueue = new ArrayList<VirtualFile>();
  private List<VFileEvent> myEvents = new ArrayList<VFileEvent>();
  private volatile boolean iHaveEventsToFire;
  private volatile RefreshWorker myWorker = null;
  private volatile boolean myCancelled = false;
  private final DumbModePermission myDumbModePermission;
  private final Throwable myStartTrace;

  public RefreshSessionImpl(boolean async, boolean recursive, @Nullable Runnable finishRunnable) {
    this(async, recursive, finishRunnable, ModalityState.NON_MODAL);
  }

  public RefreshSessionImpl(boolean async, boolean recursive, @Nullable Runnable finishRunnable, @NotNull ModalityState modalityState) {
    myIsAsync = async;
    myIsRecursive = recursive;
    myFinishRunnable = finishRunnable;
    myModalityState = modalityState;
    LOG.assertTrue(modalityState == ModalityState.NON_MODAL || modalityState != ModalityState.any(), "Refresh session should have a specific modality");

    if (modalityState == ModalityState.NON_MODAL) {
      myDumbModePermission = null;
      myStartTrace = null;
    }
    else {
      myDumbModePermission = DumbServiceImpl.getExplicitPermission();
      myStartTrace = new Throwable(); // please report exceptions here to peter
    }
  }

  public RefreshSessionImpl(@NotNull List<VFileEvent> events) {
    this(false, false, null, ModalityState.NON_MODAL);
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
        myWorkQueue.add(file);
      }
    }
  }

  @Override
  public void addFile(@NotNull VirtualFile file) {
    myWorkQueue.add(file);
  }

  @Override
  public boolean isAsynchronous() {
    return myIsAsync;
  }

  @Override
  public void launch() {
    mySemaphore.down();
    ((RefreshQueueImpl)RefreshQueue.getInstance()).execute(this);
  }

  public void scan() {
    List<VirtualFile> workQueue = myWorkQueue;
    myWorkQueue = new ArrayList<VirtualFile>();
    boolean haveEventsToFire = myFinishRunnable != null || !myEvents.isEmpty();

    if (!workQueue.isEmpty()) {
      final LocalFileSystem fileSystem = LocalFileSystem.getInstance();
      final FileWatcher watcher;
      if (fileSystem instanceof LocalFileSystemImpl) {
        LocalFileSystemImpl fs = (LocalFileSystemImpl)fileSystem;
        fs.markSuspiciousFilesDirty(workQueue);
        watcher = fs.getFileWatcher();
      }
      else {
        watcher = null;
      }

      long t = 0;
      if (LOG.isDebugEnabled()) {
        LOG.debug("scanning " + workQueue);
        t = System.currentTimeMillis();
      }

      for (VirtualFile file : workQueue) {
        if (myCancelled) break;

        NewVirtualFile nvf = (NewVirtualFile)file;
        if (!myIsRecursive && (!myIsAsync || (watcher != null && !watcher.isWatched(nvf)))) {
          // we're unable to definitely refresh synchronously by means of file watcher.
          nvf.markDirty();
        }

        RefreshWorker worker = myWorker = new RefreshWorker(nvf, myIsRecursive);
        worker.scan();
        List<VFileEvent> events = worker.getEvents();
        if (myEvents.addAll(events)) {
          haveEventsToFire = true;
        }
      }

      if (t != 0) {
        t = System.currentTimeMillis() - t;
        LOG.debug((myCancelled ? "cancelled, " : "done, ") + t + " ms, events " + myEvents);
      }
    }

    myWorker = null;
    iHaveEventsToFire = haveEventsToFire;
  }

  public void cancel() {
    myCancelled = true;

    RefreshWorker worker = myWorker;
    if (worker != null) {
      worker.cancel();
    }
  }

  public void fireEvents(final boolean hasWriteAction) {
    AccessToken token = myStartTrace == null ? null : DumbServiceImpl.forceDumbModeStartTrace(myStartTrace);
    try {
      if (!iHaveEventsToFire || ApplicationManager.getApplication().isDisposed()) return;

      Runnable runnable = new Runnable() {
        public void run() {
          if (hasWriteAction) {
            fireEventsInWriteAction();
          }
          else {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                fireEventsInWriteAction();
              }
            });
          }
        }
      };

      if (myDumbModePermission != null) {
        DumbService.allowStartingDumbModeInside(myDumbModePermission, runnable);
      } else {
        runnable.run();
      }
    }
    finally {
      if (token != null) {
        token.finish();
      }
      mySemaphore.up();
    }
  }

  protected void fireEventsInWriteAction() {
    final VirtualFileManagerEx manager = (VirtualFileManagerEx)VirtualFileManager.getInstance();

    manager.fireBeforeRefreshStart(myIsAsync);
    try {
      while (!myWorkQueue.isEmpty() || !myEvents.isEmpty()) {
        PersistentFS.getInstance().processEvents(mergeEventsAndReset());
        scan();
      }
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

  public void waitFor() {
    mySemaphore.waitFor();
  }

  private List<VFileEvent> mergeEventsAndReset() {
    Set<VFileEvent> mergedEvents = new LinkedHashSet<VFileEvent>(myEvents);
    List<VFileEvent> events = new ArrayList<VFileEvent>(mergedEvents);
    myEvents = new ArrayList<VFileEvent>();
    return events;
  }

  @NotNull
  public ModalityState getModalityState() {
    return myModalityState;
  }

  @Override
  public String toString() {
    return myWorkQueue.size() <= 1 ? "" : myWorkQueue.size() + " roots in queue.";
  }
}
