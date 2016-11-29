/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author peter
 */
final class IgnoredFileCache {
  private final ConcurrentBitSet myNonIgnoredIds = new ConcurrentBitSet();
  private final IgnoredPatternSet myIgnoredPatterns;
  private int myVfsEventNesting;

  IgnoredFileCache(@NotNull IgnoredPatternSet ignoredPatterns) {
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

      private void clearCacheForChangedFiles(@NotNull List<? extends VFileEvent> events) {
        for (final VFileEvent event : events) {
          VirtualFile file = event.getFile();
          if (file instanceof NewVirtualFile && event instanceof VFilePropertyChangeEvent) {
            int id = ((NewVirtualFile)file).getId();
            if (id >= 0) {
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

  boolean isFileIgnored(VirtualFile file) {
    int id = myVfsEventNesting == 0 && file instanceof NewVirtualFile ? ((NewVirtualFile)file).getId() : -1;
    if (id > 0 && myNonIgnoredIds.get(id)) {
      return false;
    }

    boolean result = myIgnoredPatterns.isIgnored(file.getNameSequence());
    if (!result && id > 0) {
      myNonIgnoredIds.set(id);
    }
    return result;
  }
}
