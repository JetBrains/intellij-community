/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
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
  private final boolean myIsAsync;
  private final boolean myIsRecursive;
  private final Runnable myFinishRunnable;

  private List<VirtualFile> myWorkQueue = new ArrayList<VirtualFile>();
  private List<VFileEvent> myEvents = new ArrayList<VFileEvent>();
  private final Semaphore mySemaphore = new Semaphore();
  private volatile boolean iHaveEventsToFire;

  public RefreshSessionImpl(final boolean isAsync, final boolean recursively, final Runnable finishRunnable) {
    myIsRecursive = recursively;
    myFinishRunnable = finishRunnable;
    myIsAsync = isAsync;
  }

  public RefreshSessionImpl(final List<VFileEvent> events) {
    myIsAsync = false;
    myIsRecursive = false;
    myFinishRunnable = null;
    myEvents = new ArrayList<VFileEvent>(events);
  }

  public void addAllFiles(final Collection<VirtualFile> files) {
    myWorkQueue.addAll(files);
  }

  public void addFile(@NotNull final VirtualFile file) {
    myWorkQueue.add(file);
  }

  public boolean isAsynchronous() {
    return myIsAsync;
  }

  public void launch() {
    mySemaphore.down();
    ((RefreshQueueImpl)RefreshQueue.getInstance()).execute(this);
  }

  public void scan() {
    // TODO: indicator in the status bar...
    List<VirtualFile> workQueue = myWorkQueue;
    myWorkQueue = new ArrayList<VirtualFile>();
    boolean hasEventsToFire = myFinishRunnable != null || !myEvents.isEmpty();

    if (!workQueue.isEmpty()) {
      ((LocalFileSystemImpl)LocalFileSystem.getInstance()).markSuspiciousFilesDirty(workQueue);

      for (VirtualFile file : workQueue) {
        final NewVirtualFile nvf = (NewVirtualFile)file;
        if (!myIsAsync && !myIsRecursive) { // We're unable to definitely refresh synchronously by means of file watcher.
          nvf.markDirty();
        }

        RefreshWorker worker = new RefreshWorker(file, myIsRecursive);
        worker.scan();
        List<VFileEvent> events = worker.getEvents();
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

  private void fireEventsInWriteAction() {
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
}
