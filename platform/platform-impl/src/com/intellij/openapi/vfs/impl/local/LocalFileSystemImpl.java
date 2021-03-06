// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePointerCapableFileSystem;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class LocalFileSystemImpl extends LocalFileSystemBase implements Disposable, VirtualFilePointerCapableFileSystem {
  private static final int STATUS_UPDATE_PERIOD = 1000;

  private final ManagingFS myManagingFS;
  private final FileWatcher myWatcher;
  private final WatchRootsManager myWatchRootsManager;
  private final Runnable myAfterMarkDirtyCallback;
  private volatile boolean myDisposed;

  public LocalFileSystemImpl() {
    this(null);
  }

  public LocalFileSystemImpl(@Nullable Runnable afterMarkDirtyCallback) {
    myAfterMarkDirtyCallback = afterMarkDirtyCallback;
    myManagingFS = ManagingFS.getInstance();
    myWatcher = new FileWatcher(myManagingFS, () -> {
      AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
          if (!ApplicationManager.getApplication().isDisposed()) {
            if (storeRefreshStatusToFiles() && myAfterMarkDirtyCallback != null) {
              myAfterMarkDirtyCallback.run();
            }
          }
        },
        STATUS_UPDATE_PERIOD, STATUS_UPDATE_PERIOD, TimeUnit.MILLISECONDS);
    });
    myWatchRootsManager = new WatchRootsManager(myWatcher, this);
    Disposer.register(ApplicationManager.getApplication(), this);
    new SymbolicLinkRefresher(this);
  }

  @NotNull
  public FileWatcher getFileWatcher() {
    return myWatcher;
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myWatcher.dispose();
  }

  private boolean storeRefreshStatusToFiles() {
    if (myWatcher.isOperational()) {
      FileWatcher.DirtyPaths dirtyPaths = myWatcher.getDirtyPaths();
      markPathsDirty(dirtyPaths.dirtyPaths);
      markFlatDirsDirty(dirtyPaths.dirtyDirectories);
      markRecursiveDirsDirty(dirtyPaths.dirtyPathsRecursive);
      return !dirtyPaths.dirtyPaths.isEmpty() || !dirtyPaths.dirtyDirectories.isEmpty() || !dirtyPaths.dirtyPathsRecursive.isEmpty();
    }
    return false;
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

  @Override
  public @NotNull Iterable<@NotNull VirtualFile> findCachedFilesForPath(@NotNull String path) {
    return ContainerUtil.mapNotNull(getAliasedPaths(path), this::findFileByPathIfCached);
  }

  // Finds paths that denote the same physical file (canonical path + symlinks)
  // Returns [canonical_path + symlinks], if path is canonical
  //         [path], otherwise
  private @NotNull List<@NotNull @SystemDependent String> getAliasedPaths(@NotNull String path) {
    path = FileUtil.toSystemDependentName(path);
    List<@NotNull String> aliases = new ArrayList<>(getFileWatcher().mapToAllSymlinks(path));
    assert !aliases.contains(path);
    aliases.add(0, path);
    return aliases;
  }

  @NotNull
  @Override
  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequestsToRemove,
                                               @Nullable Collection<String> recursiveRootsToAdd,
                                               @Nullable Collection<String> flatRootsToAdd) {
    if (myDisposed) return Collections.emptySet();
    Collection<WatchRequest> nonNullWatchRequestsToRemove = ContainerUtil.skipNulls(watchRequestsToRemove);
    LOG.assertTrue(nonNullWatchRequestsToRemove.size() == watchRequestsToRemove.size(), "watch requests collection should not contain `null` elements");
    return myWatchRootsManager.replaceWatchedRoots(nonNullWatchRequestsToRemove,
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

  @ApiStatus.Internal
  public final void symlinkUpdated(int fileId,
                                   @Nullable VirtualFile parent,
                                   @NotNull CharSequence name,
                                   @NotNull String linkPath,
                                   @Nullable String linkTarget) {
    if (linkTarget == null || !isRecursiveOrCircularSymlink(parent, name, linkTarget)) {
      myWatchRootsManager.updateSymlink(fileId, linkPath, linkTarget);
    }
  }

  @ApiStatus.Internal
  public final void symlinkRemoved(int fileId) {
    myWatchRootsManager.removeSymlink(fileId);
  }

  @Override
  @NonNls
  public String toString() {
    return "LocalFileSystem";
  }

  @Override
  @TestOnly
  public void cleanupForNextTest() {
    super.cleanupForNextTest();
    myWatchRootsManager.clear();
  }

  private static boolean isRecursiveOrCircularSymlink(@Nullable VirtualFile parent,
                                                      @NotNull CharSequence name,
                                                      @NotNull String symlinkTarget) {
    if (startsWith(parent, name, symlinkTarget)) return true;

    if (!(parent instanceof VirtualFileSystemEntry)) {
      return false;
    }
    // check if it's circular - any symlink above resolves to my target too
    for (VirtualFileSystemEntry p = (VirtualFileSystemEntry)parent; p != null; p = p.getParent()) {
      // optimization: when the file has no symlinks up the hierarchy, it's not circular
      if (!p.thisOrParentHaveSymlink()) return false;
      if (p.is(VFileProperty.SYMLINK)) {
        String parentResolved = p.getCanonicalPath();
        if (symlinkTarget.equals(parentResolved)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean startsWith(@Nullable VirtualFile parent,
                                    @NotNull CharSequence name,
                                    @NotNull String symlinkTarget) {
    if (parent != null) {
      String symlinkTargetParent = StringUtil.trimEnd(symlinkTarget, "/" + name);
      return VfsUtilCore.isAncestorOrSelf(symlinkTargetParent, parent);
    }
    // parent == null means name is root
    return FileUtil.PATH_CHAR_SEQUENCE_HASHING_STRATEGY.equals(name, symlinkTarget);
  }
}