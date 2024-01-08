// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PlatformNioHelper;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNullElse;

public class LocalFileSystemImpl extends LocalFileSystemBase implements Disposable, VirtualFilePointerCapableFileSystem {
  @SuppressWarnings("SSBasedInspection")
  private static final Logger WATCH_ROOTS_LOG = Logger.getInstance("#com.intellij.openapi.vfs.WatchRoots");
  private static final int STATUS_UPDATE_PERIOD = 1000;

  private final ManagingFS myManagingFS;
  private final FileWatcher myWatcher;
  private final WatchRootsManager myWatchRootsManager;
  private volatile boolean myDisposed;

  private final ThreadLocal<Pair<VirtualFile, Map<String, FileAttributes>>> myFileAttributesCache = new ThreadLocal<>();
  private final DiskQueryRelay<Pair<VirtualFile, @Nullable Set<String>>, Map<String, FileAttributes>> myChildrenAttrGetter =
    new DiskQueryRelay<>(pair -> listWithAttributes(pair.first, pair.second));

  protected LocalFileSystemImpl() {
    myManagingFS = ManagingFS.getInstance();
    myWatcher = new FileWatcher(myManagingFS, () -> {
      AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(() -> {
        Application application = ApplicationManager.getApplication();
        if (application != null && !application.isDisposed()) {
          storeRefreshStatusToFiles();
        }
      },
      STATUS_UPDATE_PERIOD, STATUS_UPDATE_PERIOD, TimeUnit.MILLISECONDS);
    });

    myWatchRootsManager = new WatchRootsManager(myWatcher, this);
    Disposer.register(ApplicationManager.getApplication(), this);
    new SymbolicLinkRefresher(this).refresh();
  }

  public void onDisconnecting() {
    //on VFS reconnect we must clear roots manager
    myWatchRootsManager.clear();
  }

  public @NotNull FileWatcher getFileWatcher() {
    return myWatcher;
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myWatcher.dispose();
  }

  private void storeRefreshStatusToFiles() {
    if (myWatcher.isOperational()) {
      var dirtyPaths = myWatcher.getDirtyPaths();
      var marked = markPathsDirty(dirtyPaths.dirtyPaths) |
                   markFlatDirsDirty(dirtyPaths.dirtyDirectories) |
                   markRecursiveDirsDirty(dirtyPaths.dirtyPathsRecursive);
      if (marked) {
        statusRefreshed();
      }
    }
  }

  protected void statusRefreshed() { }

  private boolean markPathsDirty(Iterable<String> dirtyPaths) {
    var marked = false;
    for (var dirtyPath : dirtyPaths) {
      var file = findFileByPathIfCached(dirtyPath);
      if (file instanceof NewVirtualFile nvf) {
        nvf.markDirty();
        marked = true;
      }
    }
    return marked;
  }

  private boolean markFlatDirsDirty(Iterable<String> dirtyPaths) {
    var marked = false;
    for (var dirtyPath : dirtyPaths) {
      var exactOrParent = VfsImplUtil.findCachedFileByPath(this, dirtyPath);
      if (exactOrParent.first != null) {
        exactOrParent.first.markDirty();
        for (var child : exactOrParent.first.getCachedChildren()) {
          ((NewVirtualFile)child).markDirty();
          marked = true;
        }
      }
      else if (exactOrParent.second != null) {
        exactOrParent.second.markDirty();
        marked = true;
      }
    }
    return marked;
  }

  private boolean markRecursiveDirsDirty(Iterable<String> dirtyPaths) {
    var marked = false;
    for (var dirtyPath : dirtyPaths) {
      var exactOrParent = VfsImplUtil.findCachedFileByPath(this, dirtyPath);
      if (exactOrParent.first != null) {
        exactOrParent.first.markDirtyRecursively();
        marked = true;
      }
      else if (exactOrParent.second != null) {
        exactOrParent.second.markDirty();
        marked = true;
      }
    }
    return marked;
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

  @Override
  public @NotNull Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequestsToRemove,
                                                        @Nullable Collection<String> recursiveRootsToAdd,
                                                        @Nullable Collection<String> flatRootsToAdd) {
    if (myDisposed) return Collections.emptySet();

    var nonNullWatchRequestsToRemove = ContainerUtil.skipNulls(watchRequestsToRemove);
    LOG.assertTrue(nonNullWatchRequestsToRemove.size() == watchRequestsToRemove.size(), "watch requests collection should not contain `null` elements");

    if ((recursiveRootsToAdd != null || flatRootsToAdd != null) && WATCH_ROOTS_LOG.isTraceEnabled()) {
      WATCH_ROOTS_LOG.trace(new Exception("LocalFileSystemImpl#replaceWatchedRoots:" +
                                          "\n  recursive: " + (recursiveRootsToAdd != null ? recursiveRootsToAdd : "[]") +
                                          "\n  flat: " + (flatRootsToAdd != null ? flatRootsToAdd : "[]")));
    }

    return myWatchRootsManager.replaceWatchedRoots(nonNullWatchRequestsToRemove,
                                                   requireNonNullElse(recursiveRootsToAdd, Collections.emptyList()),
                                                   requireNonNullElse(flatRootsToAdd, Collections.emptyList()));
  }

  @Override
  public void refreshWithoutFileWatcher(boolean asynchronous) {
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
  @TestOnly
  public void cleanupForNextTest() {
    super.cleanupForNextTest();
    myWatchRootsManager.clear();
  }

  private static boolean isRecursiveOrCircularSymlink(@Nullable VirtualFile parent, CharSequence name, String symlinkTarget) {
    if (startsWith(parent, name, symlinkTarget)) return true;
    if (!(parent instanceof VirtualFileSystemEntry)) return false;
    // check if it's circular - any symlink above resolves to my target too
    for (VirtualFileSystemEntry p = (VirtualFileSystemEntry)parent; p != null; p = p.getParent()) {
      // if the file has no symlinks up the hierarchy, it's not circular
      if (!p.thisOrParentHaveSymlink()) return false;
      if (p.is(VFileProperty.SYMLINK)) {
        String parentResolved = p.getCanonicalPath();
        if (symlinkTarget.equals(parentResolved)) return true;
      }
    }
    return false;
  }

  private static boolean startsWith(@Nullable VirtualFile parent, CharSequence name, String symlinkTarget) {
    // parent == null means name is root
    //noinspection StaticMethodReferencedViaSubclass
    return parent != null ? VfsUtilCore.isAncestorOrSelf(StringUtil.trimEnd(symlinkTarget, "/" + name), parent)
                          : StringUtil.equal(name, symlinkTarget, SystemInfo.isFileSystemCaseSensitive);
  }

  @ApiStatus.Internal
  public String @NotNull [] listWithCaching(@NotNull VirtualFile dir) {
    return listWithCaching(dir, null);
  }

  @ApiStatus.Internal
  public String @NotNull [] listWithCaching(@NotNull VirtualFile dir, @Nullable Set<String> filter) {
    if ((SystemInfo.isWindows || SystemInfo.isMac && CpuArch.isArm64()) && getClass() == LocalFileSystemImpl.class) {
      Pair<VirtualFile, Map<String, FileAttributes>> cache = myFileAttributesCache.get();
      if (cache != null) {
        LOG.error("unordered access to " + dir + " without cleaning after " + cache.first);
      }
      Map<String, FileAttributes> result = myChildrenAttrGetter.accessDiskWithCheckCanceled(new Pair<>(dir, filter));
      myFileAttributesCache.set(new Pair<>(dir, result));
      return ArrayUtil.toStringArray(result.keySet());
    }
    else {
      return list(dir);
    }
  }

  @ApiStatus.Internal
  public void clearListCache() {
    myFileAttributesCache.remove();
  }

  @Override
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    Pair<VirtualFile, Map<String, FileAttributes>> cache = myFileAttributesCache.get();
    if (cache != null) {
      if (!cache.first.equals(file.getParent())) {
        LOG.error("unordered access to " + file + " outside " + cache.first);
      }
      else {
        return cache.second.get(file.getName());
      }
    }

    return super.getAttributes(file);
  }

  private static Map<String, FileAttributes> listWithAttributes(VirtualFile dir, @Nullable Set<String> filter) {
    try {
      var list = CollectionFactory.<FileAttributes>createFilePathMap(10, dir.isCaseSensitive());

      PlatformNioHelper.visitDirectory(Path.of(toIoPath(dir)), filter, (file, result) -> {
        try {
          var attrs = copyWithCustomTimestamp(file, FileAttributes.fromNio(file, result.get()));
          list.put(file.getFileName().toString(), attrs);
        }
        catch (Exception e) { LOG.debug(e); }
        return true;
      });

      return list;
    }
    catch (AccessDeniedException | NoSuchFileException e) { LOG.debug(e); }
    catch (IOException | RuntimeException e) { LOG.warn(e); }
    return Map.of();
  }

  private static FileAttributes copyWithCustomTimestamp(Path file, FileAttributes attributes) {
    for (LocalFileSystemTimestampEvaluator provider : LocalFileSystemTimestampEvaluator.EP_NAME.getExtensionList()) {
      Long custom = provider.getTimestamp(file);
      if (custom != null) {
        return attributes.withTimeStamp(custom);
      }
    }

    return attributes;
  }

  @Override
  public String toString() {
    return "LocalFileSystem";
  }
}
