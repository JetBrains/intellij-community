/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.impl.local.FileWatcher;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class RefreshSessionImpl extends RefreshSession {
  private static final Logger LOG = Logger.getInstance(RefreshSession.class);

  private final boolean myIsAsync;
  private final boolean myIsRecursive;
  private final Runnable myFinishRunnable;
  private final ModalityState myModalityState;
  private final Semaphore mySemaphore = new Semaphore();

  private List<VirtualFile> myWorkQueue = new ArrayList<VirtualFile>();
  private List<VFileEvent> myEvents = new ArrayList<VFileEvent>();
  private volatile boolean iHaveEventsToFire;

  public RefreshSessionImpl(final boolean isAsync, final boolean recursively, final Runnable finishRunnable) {
    this(isAsync, recursively, finishRunnable, ModalityState.NON_MODAL);
  }

  public RefreshSessionImpl(final boolean isAsync, final boolean recursively, final Runnable finishRunnable, ModalityState modalityState) {
    myIsRecursive = recursively;
    myFinishRunnable = finishRunnable;
    myIsAsync = isAsync;
    myModalityState = modalityState;
  }

  public RefreshSessionImpl(final List<VFileEvent> events) {
    myIsAsync = false;
    myIsRecursive = false;
    myFinishRunnable = null;
    myEvents = new ArrayList<VFileEvent>(events);
    myModalityState = ModalityState.NON_MODAL;
  }

  @Override
  public void addAllFiles(final Collection<VirtualFile> files) {
    myWorkQueue.addAll(files);
  }

  @Override
  public void addFile(@NotNull final VirtualFile file) {
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
    boolean hasEventsToFire = myFinishRunnable != null || !myEvents.isEmpty();

    if (!workQueue.isEmpty()) {
      LocalFileSystemImpl fs = (LocalFileSystemImpl)LocalFileSystem.getInstance();
      fs.markSuspiciousFilesDirty(workQueue);
      FileWatcher watcher = fs.getFileWatcher();

      for (VirtualFile file : workQueue) {
        NewVirtualFile nvf = (NewVirtualFile)file;
        if (!myIsRecursive && (!myIsAsync || !watcher.isWatched(nvf))) { // We're unable to definitely refresh synchronously by means of file watcher.
          nvf.markDirty();
        }

        RefreshWorker worker = new RefreshWorker(file, myIsRecursive);
        long t = LOG.isDebugEnabled() ? System.currentTimeMillis() : 0;
        worker.scan();
        List<VFileEvent> events = worker.getEvents();
        if (t != 0) {
          t = System.currentTimeMillis() - t;
          LOG.debug(file + " scanned in " + t + " ms, events: " + events);
        }
        myEvents.addAll(events);
        if (!events.isEmpty()) hasEventsToFire = true;
      }
    }
    iHaveEventsToFire = hasEventsToFire;
  }

  public void fireEvents(boolean hasWriteAction) {
    try {
      if (!iHaveEventsToFire) return;

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
    finally {
      mySemaphore.up();
    }
  }

  protected void fireEventsInWriteAction() {
    final VirtualFileManagerEx manager = (VirtualFileManagerEx)VirtualFileManager.getInstance();

    manager.fireBeforeRefreshStart(myIsAsync);
    try {
      while (!myWorkQueue.isEmpty() || !myEvents.isEmpty()) {
        ManagingFS.getInstance().processEvents(mergeEventsAndReset());
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
    LinkedHashSet<VFileEvent> mergedEvents = new LinkedHashSet<VFileEvent>(myEvents);
    List<VFileEvent> events = new ArrayList<VFileEvent>(mergedEvents);
    myEvents = new ArrayList<VFileEvent>();
    return events;
  }

  @NotNull
  public ModalityState getModalityState() {
    return myModalityState;
  }
}
