// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.util;

import com.intellij.openapi.vfs.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public class NewFileTracker {
  private final Set<VirtualFile> newFiles = Collections.synchronizedSet(new THashSet<>());

  public boolean poll(@NotNull VirtualFile file) {
    return newFiles.remove(file);
  }

  public NewFileTracker() {
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
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

  public void clear() {
    newFiles.clear();
  }
}