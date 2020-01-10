// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePointerCapableFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class LocalFileSystemImpl extends LocalFileSystemBase implements Disposable, VirtualFilePointerCapableFileSystem {
  private static final String FS_ROOT = "/";
  private static final int STATUS_UPDATE_PERIOD = 1000;

  private final ManagingFS myManagingFS;
  private final FileWatcher myWatcher;

  private final WatchRootsMap myWatchRootsMap ;

  public LocalFileSystemImpl() {
    myManagingFS = ManagingFS.getInstance();
    myWatcher = new FileWatcher(myManagingFS);
    if (myWatcher.isOperational()) {
      AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
        () -> { if (!ApplicationManager.getApplication().isDisposed()) storeRefreshStatusToFiles(); },
        STATUS_UPDATE_PERIOD, STATUS_UPDATE_PERIOD, TimeUnit.MILLISECONDS);
    }
    myWatchRootsMap = new WatchRootsMap(myWatcher, this);
    Disposer.register(ApplicationManager.getApplication(), this);
  }

  @NotNull
  public FileWatcher getFileWatcher() {
    return myWatcher;
  }

  @Override
  public void dispose() {
    myWatcher.dispose();
  }

  private void storeRefreshStatusToFiles() {
    if (myWatcher.isOperational()) {
      FileWatcher.DirtyPaths dirtyPaths = myWatcher.getDirtyPaths();
      markPathsDirty(dirtyPaths.dirtyPaths);
      markFlatDirsDirty(dirtyPaths.dirtyDirectories);
      markRecursiveDirsDirty(dirtyPaths.dirtyPathsRecursive);
    }
  }

  private void markPathsDirty(@NotNull Iterable<String> dirtyPaths) {
    for (String dirtyPath : dirtyPaths) {
      VirtualFile file = findFileByPathIfCached(dirtyPath);
      if (file instanceof NewVirtualFile) {
        ((NewVirtualFile)file).markDirty();
      }
    }
  }

  private void markFlatDirsDirty(@NotNull Iterable<String> dirtyPaths) {
    for (String dirtyPath : dirtyPaths) {
      Pair<NewVirtualFile, NewVirtualFile> pair = VfsImplUtil.findCachedFileByPath(this, dirtyPath);
      if (pair.first != null) {
        pair.first.markDirty();
        for (VirtualFile child : pair.first.getCachedChildren()) {
          ((NewVirtualFile)child).markDirty();
        }
      }
      else if (pair.second != null) {
        pair.second.markDirty();
      }
    }
  }

  private void markRecursiveDirsDirty(@NotNull Iterable<String> dirtyPaths) {
    for (String dirtyPath : dirtyPaths) {
      Pair<NewVirtualFile, NewVirtualFile> pair = VfsImplUtil.findCachedFileByPath(this, dirtyPath);
      if (pair.first != null) {
        pair.first.markDirtyRecursively();
      }
      else if (pair.second != null) {
        pair.second.markDirty();
      }
    }
  }

  public void markSuspiciousFilesDirty(@NotNull List<? extends VirtualFile> files) {
    storeRefreshStatusToFiles();

    if (myWatcher.isOperational()) {
      for (String root : myWatcher.getManualWatchRoots()) {
        VirtualFile suspiciousRoot = findFileByPathIfCached(root);
        if (suspiciousRoot != null) {
          ((NewVirtualFile)suspiciousRoot).markDirtyRecursively();
        }
      }
    }
    else {
      for (VirtualFile file : files) {
        if (file.getFileSystem() == this) {
          ((NewVirtualFile)file).markDirtyRecursively();
        }
      }
    }
  }

  @NotNull
  @Override
  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequestsToRemove,
                                               @Nullable Collection<String> recursiveRootsToAdd,
                                               @Nullable Collection<String> flatRootsToAdd) {
    Collection<WatchRequest> nonNullWatchRequestsToRemove = ContainerUtil.skipNulls(watchRequestsToRemove);
    LOG.assertTrue(nonNullWatchRequestsToRemove.size() == watchRequestsToRemove.size(), "watch requests collection should not contain `null` elements");
    return myWatchRootsMap.replaceWatchedRoots(nonNullWatchRequestsToRemove,
                                               ObjectUtils.notNull(recursiveRootsToAdd, Collections.emptyList()),
                                               ObjectUtils.notNull(flatRootsToAdd, Collections.emptyList()));

  }
  @Override
  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    Runnable heavyRefresh = () -> {
      for (VirtualFile root : myManagingFS.getRoots(this)) {
        ((NewVirtualFile)root).markDirtyRecursively();
      }
      refresh(asynchronous);
    };

    if (asynchronous && myWatcher.isOperational()) {
      RefreshQueue.getInstance().refresh(true, true, heavyRefresh, myManagingFS.getRoots(this));
    }
    else {
      heavyRefresh.run();
    }
  }

  @Override
  public String toString() {
    return "LocalFileSystem";
  }

  @TestOnly
  public void cleanupForNextTest() {
    FileDocumentManager.getInstance().saveAllDocuments();
    PersistentFS.getInstance().clearIdCache();
    myWatchRootsMap.clear();
  }
}
