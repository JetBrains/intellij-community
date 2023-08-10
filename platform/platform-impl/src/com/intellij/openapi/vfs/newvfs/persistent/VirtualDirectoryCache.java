// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class VirtualDirectoryCache {
  // FS roots only (dirs with .getParent()==null)
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myIdToRootCache = ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  // FS inner dirs only (dirs with .getParent()!=null), separated from the root cache to speedup clear
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myIdToDirCache = ConcurrentCollectionFactory.createConcurrentIntObjectSoftValueMap();

  @NotNull
  VirtualFileSystemEntry getOrCacheDir(@NotNull VirtualFileSystemEntry newDir) {
    int id = newDir.getId();
    ConcurrentIntObjectMap<VirtualFileSystemEntry> cache = getCache(newDir);
    VirtualFileSystemEntry dir = cache.get(id);
    if (dir != null) return dir;
    return cache.cacheOrGet(id, newDir);
  }

  private ConcurrentIntObjectMap<VirtualFileSystemEntry> getCache(@NotNull VirtualFileSystemEntry newDir) {
    return newDir.getParent() == null ? myIdToRootCache : myIdToDirCache;
  }

  void cacheDir(@NotNull VirtualFileSystemEntry newDir) {
    getCache(newDir).put(newDir.getId(), newDir);
  }

  @Nullable
  VirtualFileSystemEntry cacheDirIfAbsent(@NotNull VirtualFileSystemEntry newDir) {
    return getCache(newDir).putIfAbsent(newDir.getId(), newDir);
  }

  @Nullable
  VirtualFileSystemEntry getCachedDir(int id) {
    VirtualFileSystemEntry dir = myIdToDirCache.get(id);
    if (dir != null) return dir;
    return myIdToRootCache.get(id);
  }

  @Nullable
  VirtualFileSystemEntry getCachedRoot(int id) {
    return myIdToRootCache.get(id);
  }

  void dropNonRootCachedDirs() {
    myIdToDirCache.clear();
  }

  void remove(int id) {
    myIdToDirCache.remove(id);
    myIdToRootCache.remove(id);
  }

  @TestOnly
  @NotNull
  Iterable<VirtualFileSystemEntry> getCachedDirs() {
    return ContainerUtil.concat(myIdToDirCache.values(), myIdToRootCache.values());
  }

  @NotNull
  Iterable<VirtualFileSystemEntry> getCachedRootDirs() {
    return myIdToRootCache.values();
  }

  void clear() {
    myIdToDirCache.clear();
    myIdToRootCache.clear();
  }
}
