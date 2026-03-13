// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListenerBackgroundable;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

final class IgnoredFileCache {
  private final ConcurrentBitSet myNonIgnoredIds = ConcurrentBitSet.create();
  private final Predicate<? super CharSequence> myIsIgnored;
  private AtomicInteger myVfsEventNesting = new AtomicInteger();

  IgnoredFileCache(@NotNull Predicate<? super CharSequence> isIgnored) {
    myIsIgnored = isIgnored;
    MessageBusConnection connect = ApplicationManager.getApplication().getMessageBus().connect();
    connect.subscribe(VirtualFileManager.VFS_CHANGES_BG, new BulkFileListenerBackgroundable() {
      @Override
      public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
        // during VFS event processing the system may be in inconsistent state, don't cache it
        myVfsEventNesting.incrementAndGet();
        clearCacheForChangedFiles(events);
      }

      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        clearCacheForChangedFiles(events);
        myVfsEventNesting.decrementAndGet();
      }

      private void clearCacheForChangedFiles(@NotNull List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          if (event instanceof VFilePropertyChangeEvent && ((VFilePropertyChangeEvent)event).isRename()) {
            VirtualFile file = event.getFile();
            if (file instanceof NewVirtualFile) {
              int id = ((NewVirtualFile)file).getId();
              myNonIgnoredIds.clear(id);
            }
          }
        }
      }
    });
  }

  void clearCache() {
    myNonIgnoredIds.clear();
  }

  boolean isFileIgnored(@NotNull VirtualFile file) {
    boolean idable = myVfsEventNesting.get() == 0 && file instanceof NewVirtualFile;
    if (!idable) {
      return calcIgnored(file);
    }
    int id = ((NewVirtualFile)file).getId();
    if (myNonIgnoredIds.get(id)) {
      return false;
    }

    return calcAndCache(file, id);
  }

  private boolean calcAndCache(VirtualFile file, int id) {
    boolean result = calcIgnored(file);
    if (!result) {
      myNonIgnoredIds.set(id);
    }
    return result;
  }

  private boolean calcIgnored(@NotNull VirtualFile file) {
    return myIsIgnored.test(file.getNameSequence());
  }
}