/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.containers.IntArrayList;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.List;

/**
 * @author peter
 */
class IgnoredFileCache {
  private final BitSet myCheckedIds = new BitSet();
  private final TIntHashSet myIgnoredIds = new TIntHashSet();
  private final IgnoredPatternSet myIgnoredPatterns;
  private int myVfsEventNesting = 0;

  IgnoredFileCache(IgnoredPatternSet ignoredPatterns) {
    myIgnoredPatterns = ignoredPatterns;
    MessageBusConnection connect = ApplicationManager.getApplication().getMessageBus().connect();
    connect.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull List<? extends VFileEvent> events) {
        // during VFS event processing the system may be in inconsistent state, don't cache it
        myVfsEventNesting++;
        clearCacheForChangedFiles(events);
      }

      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        clearCacheForChangedFiles(events);
        myVfsEventNesting--;
      }

      private void clearCacheForChangedFiles(List<? extends VFileEvent> events) {
        final IntArrayList ids = collectChangedIds(events);
        synchronized (myCheckedIds) {
          for (int i : ids.toArray()) {
            myCheckedIds.clear(i);
          }
        }
      }

      private IntArrayList collectChangedIds(List<? extends VFileEvent> events) {
        final IntArrayList ids = new IntArrayList();
        for (final VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (!(file instanceof NewVirtualFile)) {
            continue;
          }

          if (event instanceof VFilePropertyChangeEvent) {
            int id = ((NewVirtualFile)file).getId();
            if (id >= 0) {
              ids.add(id);
            }
          }
        }
        return ids;
      }
    });
  }

  void clearCache() {
    synchronized (myCheckedIds) {
      myCheckedIds.clear();
      myIgnoredIds.clear();
    }
  }

  boolean isFileIgnored(VirtualFile file) {
    if (myVfsEventNesting != 0 || !(file instanceof NewVirtualFile)) {
      return isFileIgnoredNoCache(file);
    }

    int id = ((NewVirtualFile)file).getId();
    if (id < 0) {
      return isFileIgnoredNoCache(file);
    }

    synchronized (myCheckedIds) {
      if (myCheckedIds.get(id)) {
        return myIgnoredIds.contains(id);
      }
    }

    boolean result = isFileIgnoredNoCache(file);
    synchronized (myCheckedIds) {
      myCheckedIds.set(id);
      if (result) {
        myIgnoredIds.add(id);
      } else {
        myIgnoredIds.remove(id);
      }
    }
    return result;
  }

  private boolean isFileIgnoredNoCache(VirtualFile file) {
    return myIgnoredPatterns.isIgnored(file.getName());
  }
}
