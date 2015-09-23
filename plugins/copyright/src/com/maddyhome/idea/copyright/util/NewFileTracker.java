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

package com.maddyhome.idea.copyright.util;

import com.intellij.openapi.vfs.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class NewFileTracker {
  public static NewFileTracker getInstance() {
    return instance;
  }

  public boolean poll(@NotNull VirtualFile file) {
    return newFiles.remove(file);
  }

  private NewFileTracker() {
    final VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
    virtualFileManager.addVirtualFileListener(new VirtualFileAdapter() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        if (event.isFromRefresh()) return;
        newFiles.add(event.getFile());
      }

      @Override
      public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        if (event.isFromRefresh()) return;
        newFiles.add(event.getFile());
      }
    });
  }

  private final Set<VirtualFile> newFiles = Collections.synchronizedSet(new THashSet<VirtualFile>());
  private static final NewFileTracker instance = new NewFileTracker();

  public void clear() {
    newFiles.clear();
  }
}