// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static com.intellij.concurrency.ConcurrentCollectionFactory.createConcurrentIntObjectMap;
import static com.intellij.concurrency.ConcurrentCollectionFactory.createConcurrentIntObjectSoftValueMap;

final class VirtualDirectoryCache {

  /** FS roots only (dirs with .getParent()==null) */
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> idToRootCache = createConcurrentIntObjectMap();

  /** FS inner dirs only (dirs with .getParent()!=null), separated from the root cache to speedup clear */
  private final ConcurrentIntObjectMap<VirtualFileSystemEntry> idToDirCache = createConcurrentIntObjectSoftValueMap();

  @NotNull
  VirtualFileSystemEntry getOrCacheDir(@NotNull VirtualFileSystemEntry newDir) {
    int id = newDir.getId();
    ConcurrentIntObjectMap<VirtualFileSystemEntry> cache = getCache(newDir);
    VirtualFileSystemEntry dir = cache.get(id);
    if (dir != null) return dir;
    return cache.cacheOrGet(id, newDir);
  }

  private ConcurrentIntObjectMap<VirtualFileSystemEntry> getCache(@NotNull VirtualFileSystemEntry newDir) {
    return newDir.getParent() == null ? idToRootCache : idToDirCache;
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
    VirtualFileSystemEntry dir = idToDirCache.get(dirId);
    if (dir != null) return dir;
    return idToRootCache.get(dirId);
  }

  @Nullable
  VirtualFileSystemEntry getCachedRoot(int rootId) {
    return idToRootCache.get(rootId);
  }

  @TestOnly
  @NotNull
  Iterable<VirtualFileSystemEntry> getCachedDirs() {
    return ContainerUtil.concat(idToDirCache.values(), idToRootCache.values());
  }

  @NotNull
  Iterable<VirtualFileSystemEntry> getCachedRootDirs() {
    return idToRootCache.values();
  }

  void dropNonRootCachedDirs() {
    idToDirCache.clear();
  }

  /** Drops fileId from the cache completely -- from both roots and directories caches */
  void drop(int fileId) {
    idToDirCache.remove(fileId);
    idToRootCache.remove(fileId);
  }

  /** Drops all entries from cache completely -- i.e. from both roots and directories caches */
  void clear() {
    idToDirCache.clear();
    idToRootCache.clear();
  }
}
