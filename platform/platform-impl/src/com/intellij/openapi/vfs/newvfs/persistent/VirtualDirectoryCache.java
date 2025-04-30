// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static com.intellij.concurrency.ConcurrentCollectionFactory.createConcurrentIntObjectSoftValueMap;

final class VirtualDirectoryCache {

  /** FS roots only (dirs with .getParent()==null) */
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myIdToRootCache = ConcurrentCollectionFactory.createConcurrentIntObjectMap();

  /** FS inner dirs only (dirs with .getParent()!=null), separated from the root cache to speedup clear */
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> myIdToDirCache = createConcurrentIntObjectSoftValueMap();

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
  VirtualFileSystemEntry getCachedDir(int dirId) {
    VirtualFileSystemEntry dir = myIdToDirCache.get(dirId);
    if (dir != null) return dir;
    return myIdToRootCache.get(dirId);
  }

  @Nullable
  VirtualFileSystemEntry getCachedRoot(int rootId) {
    return myIdToRootCache.get(rootId);
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

  void dropNonRootCachedDirs() {
    myIdToDirCache.clear();
  }

  /** Drops fileId from the cache completely -- from both roots and directories caches */
  void drop(int fileId) {
    myIdToDirCache.remove(fileId);
    myIdToRootCache.remove(fileId);
  }

  /** Drops all entries from cache completely -- i.e. from both roots and directories caches */
  void clear() {
    myIdToDirCache.clear();
    myIdToRootCache.clear();
  }
}
