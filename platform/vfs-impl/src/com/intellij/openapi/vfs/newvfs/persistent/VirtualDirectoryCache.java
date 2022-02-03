// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

final class VirtualDirectoryCache {
  // FS roots must be in this map too. findFileById() relies on this.
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myIdToDirCache =
    ConcurrentCollectionFactory.createConcurrentIntObjectSoftValueMap();

  @NotNull VirtualFileSystemEntry getOrCacheDir(@NotNull VirtualFileSystemEntry newDir) {
    int id = newDir.getId();
    VirtualFileSystemEntry dir = myIdToDirCache.get(id);
    if (dir != null) return dir;
    return myIdToDirCache.cacheOrGet(id, newDir);
  }

  void cacheDir(@NotNull VirtualFileSystemEntry newDir) {
    myIdToDirCache.put(newDir.getId(), newDir);
  }

  @Nullable VirtualFileSystemEntry cacheDirIfAbsent(@NotNull VirtualFileSystemEntry newDir) {
    return myIdToDirCache.putIfAbsent(newDir.getId(), newDir);
  }

  @Nullable VirtualFileSystemEntry getCachedDir(int id) {
    return myIdToDirCache.get(id);
  }

  void dropNonRootCachedDirs() {
    myIdToDirCache.entrySet().removeIf(e -> e.getValue().getParent() != null);
  }

  void remove(int id) {
    myIdToDirCache.remove(id);
  }

  @NotNull Collection<VirtualFileSystemEntry> getCachedDirs() {
    return myIdToDirCache.values();
  }

  void clear() {
    myIdToDirCache.clear();
  }
}
