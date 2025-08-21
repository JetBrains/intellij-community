// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.Disposable;
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
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.persistent.BatchingFileSystem;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PlatformNioHelper;
import org.jetbrains.annotations.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static com.intellij.util.containers.CollectionFactory.createFilePathMap;
import static java.util.Objects.requireNonNullElse;

@ApiStatus.Internal
@SuppressWarnings("removal")
public class LocalFileSystemImpl extends LocalFileSystemBase implements Disposable,
                                                                        BatchingFileSystem,
                                                                        VirtualFilePointerCapableFileSystem {
  @SuppressWarnings("SSBasedInspection")
  private static final Logger WATCH_ROOTS_LOG = Logger.getInstance("#com.intellij.openapi.vfs.WatchRoots");
  private static final int STATUS_UPDATE_PERIOD = 1000;

  private static final FileAttributes UNC_ROOT_ATTRIBUTES =
    new FileAttributes(true, false, false, false, DEFAULT_LENGTH, DEFAULT_TIMESTAMP, false, FileAttributes.CaseSensitivity.INSENSITIVE);

  private final ManagingFS myManagingFS;
  private final FileWatcher myWatcher;
  private final WatchRootsManager myWatchRootsManager;
  private volatile boolean myDisposed;

  private final ThreadLocal<Pair<VirtualFile, Map<String, FileAttributes>>> myFileAttributesCache = new ThreadLocal<>();
  private final DiskQueryRelay<VirtualFile, String[]> myChildrenGetter = new DiskQueryRelay<>(dir -> listChildren(dir));
  private final DiskQueryRelay<VirtualFile, Object> myContentGetter = new DiskQueryRelay<>(file -> readContent(file));
  private final DiskQueryRelay<VirtualFile, FileAttributes> myAttributeGetter = new DiskQueryRelay<>(file -> readAttributes(file));
  private final DiskQueryRelay<Pair<VirtualFile, @Nullable Set<String>>, Map<String, FileAttributes>> myChildrenAttrGetter =
    new DiskQueryRelay<>(pair -> listWithAttributesImpl(pair.first, pair.second));

  protected LocalFileSystemImpl() {
    myManagingFS = ManagingFS.getInstance();
    myWatcher = new FileWatcher(myManagingFS, () -> {
      AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
        () -> {
          var application = ApplicationManager.getApplication();
          if (application != null && !application.isDisposed()) {
            storeRefreshStatusToFiles();
          }
        },
        STATUS_UPDATE_PERIOD, STATUS_UPDATE_PERIOD, TimeUnit.MILLISECONDS
      );
    });
    myWatchRootsManager = new WatchRootsManager(myWatcher, this);
    Disposer.register(ApplicationManager.getApplication(), this);
    new SymbolicLinkRefresher(this).refresh();
  }

  public void onDisconnecting() {
    // on VFS reconnect, we must clear roots manager
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
      var exactOrParent = findCachedFileByPath(this, dirtyPath);
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
      var exactOrParent = findCachedFileByPath(this, dirtyPath);
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
      for (var root : myWatcher.getManualWatchRoots()) {
        var suspiciousRoot = findFileByPathIfCached(root);
        if (suspiciousRoot != null) {
          ((NewVirtualFile)suspiciousRoot).markDirtyRecursively();
        }
      }
    }
    else {
      for (var file : files) {
        if (file.getFileSystem() == this) {
          ((NewVirtualFile)file).markDirtyRecursively();
        }
      }
    }
  }

  @Override
  public @Unmodifiable @NotNull Iterable<@NotNull VirtualFile> findCachedFilesForPath(@NotNull String path) {
    return ContainerUtil.mapNotNull(getAliasedPaths(path), path1 -> findFileByPathIfCached(path1));
  }

  // Finds paths that denote the same physical file (canonical path + symlinks).
  // Returns `[canonical_path + symlinks]` if the path is canonical, `[path]` otherwise.
  private List<@SystemDependent String> getAliasedPaths(String path) {
    path = FileUtil.toSystemDependentName(path);
    var aliases = new ArrayList<>(getFileWatcher().mapToAllSymlinks(path));
    assert !aliases.contains(path);
    aliases.add(0, path);
    return aliases;
  }

  @Override
  public @NotNull Set<WatchRequest> replaceWatchedRoots(
    @NotNull Collection<WatchRequest> watchRequestsToRemove,
    @Nullable Collection<String> recursiveRootsToAdd,
    @Nullable Collection<String> flatRootsToAdd
  ) {
    if (myDisposed) return Set.of();

    var nonNullWatchRequestsToRemove = ContainerUtil.skipNulls(watchRequestsToRemove);
    LOG.assertTrue(nonNullWatchRequestsToRemove.size() == watchRequestsToRemove.size(), "watch requests collection should not contain `null` elements");

    if ((recursiveRootsToAdd != null || flatRootsToAdd != null) && WATCH_ROOTS_LOG.isTraceEnabled()) {
      WATCH_ROOTS_LOG.trace(new Exception("LocalFileSystemImpl#replaceWatchedRoots:" +
                                          "\n  recursive: " + (recursiveRootsToAdd != null ? recursiveRootsToAdd : "[]") +
                                          "\n  flat: " + (flatRootsToAdd != null ? flatRootsToAdd : "[]")));
    }

    return myWatchRootsManager.replaceWatchedRoots(
      nonNullWatchRequestsToRemove,
      requireNonNullElse(recursiveRootsToAdd, List.of()),
      requireNonNullElse(flatRootsToAdd, List.of()));
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
  public final void symlinkUpdated(
    int fileId,
    @Nullable VirtualFile parent,
    @NotNull CharSequence name,
    @NotNull String linkPath,
    @Nullable String linkTarget
  ) {
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
    for (var p = (VirtualFileSystemEntry)parent; p != null; p = p.getParent()) {
      // if the file has no symlinks up the hierarchy, it's not circular
      if (!p.thisOrParentHaveSymlink()) return false;
      if (p.is(VFileProperty.SYMLINK)) {
        var parentResolved = p.getCanonicalPath();
        if (symlinkTarget.equals(parentResolved)) return true;
      }
    }
    return false;
  }

  private static boolean startsWith(@Nullable VirtualFile parent, CharSequence name, String symlinkTarget) {
    // parent == null means name is root
    return parent != null ? VfsUtilCore.isAncestorOrSelf(StringUtil.trimEnd(symlinkTarget, "/" + name), parent)
                          : StringUtil.equal(name, symlinkTarget, SystemInfo.isFileSystemCaseSensitive);
  }

  @Override
  public String @NotNull [] list(@NotNull VirtualFile file) {
    if (!file.isDirectory()) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    return myChildrenGetter.accessDiskWithCheckCanceled(file);
  }

  private final DiskQueryRelay<Pair<VirtualFile, String>, FileAttributes.CaseSensitivity> caseSensitivityGetter = new DiskQueryRelay<>(
    it -> super.fetchCaseSensitivity(it.first, it.second)
  );

  @Override
  public @NotNull FileAttributes.CaseSensitivity fetchCaseSensitivity(@NotNull VirtualFile parent,
                                                                      @NotNull String childName) {
    return caseSensitivityGetter.accessDiskWithCheckCanceled(Pair.createNonNull(parent, childName));
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    if (SystemInfo.isUnix && file.is(VFileProperty.SPECIAL)) { // avoid opening FIFO files
      throw new NoSuchFileException(file.getPath(), null, "Not a file");
    }
    var result = myContentGetter.accessDiskWithCheckCanceled(file);
    if (result instanceof IOException e) throw e;
    return (byte[])result;
  }

  /**
   * @deprecated prefer to use {@link #listWithAttributes(VirtualFile, Set)} instead -- it is stateless, hence its
   * behavior is more predictable
   */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public final String @NotNull [] listWithCaching(@NotNull VirtualFile dir,
                                                  @Nullable Set<String> filter) {
    var cache = myFileAttributesCache.get();
    if (cache != null) {
      LOG.error("unordered access to " + dir + " without cleaning after " + cache.first);
    }
    var result = myChildrenAttrGetter.accessDiskWithCheckCanceled(new Pair<>(dir, filter));
    myFileAttributesCache.set(new Pair<>(dir, result));
    return ArrayUtil.toStringArray(result.keySet());
  }

  /** @deprecated see {@link #listWithCaching(VirtualFile, Set)} docs for reasoning */
  @ApiStatus.Internal
  @Deprecated(forRemoval = true)
  public void clearListCache() {
    myFileAttributesCache.remove();
  }

  @Override
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    if (SystemInfo.isWindows && file.getParent() == null && file.getPath().startsWith("//")) {
      return UNC_ROOT_ATTRIBUTES;
    }

    var cache = myFileAttributesCache.get();
    if (cache != null) {
      if (!cache.first.equals(file.getParent())) {
        LOG.error("unordered access to " + file + " outside " + cache.first);
      }
      else {
        return cache.second.get(file.getName());
      }
    }

    return myAttributeGetter.accessDiskWithCheckCanceled(file);
  }

  private static String[] listChildren(VirtualFile dir) {
    if (!dir.isDirectory()) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }
    try (var dirStream = Files.newDirectoryStream(Path.of(toIoPath(dir)))) {
      return StreamSupport.stream(dirStream.spliterator(), false)
        .map(it -> it.getFileName().toString())
        .toArray(String[]::new);
    }
    catch (AccessDeniedException | NoSuchFileException e) { LOG.debug(e); }
    catch (IOException | RuntimeException e) { LOG.warn(e); }
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public @NotNull Map<@NotNull String, @NotNull FileAttributes> listWithAttributes(@NotNull VirtualFile dir,
                                                                                   @Nullable Set<String> childrenNames) {
    if (!dir.isDirectory()) {
      return Collections.emptyMap();
    }
    return myChildrenAttrGetter.accessDiskWithCheckCanceled(new Pair<>(dir, childrenNames));
  }

  private static Map<String, FileAttributes> listWithAttributesImpl(@NotNull VirtualFile dir,
                                                                    @Nullable Set<String> filter) {
    if (!dir.isDirectory()) {
      return Collections.emptyMap();
    }
    try {
      int expectedSize = (filter == null) ? 10 : filter.size();
      //We must return a 'normal' (=case-sensitive) map from this method, see BatchingFileSystem.listWithAttributes() contract:
      Map<String, FileAttributes> childrenWithAttributes = createFilePathMap(expectedSize, /*caseSensitive: */true);

      PlatformNioHelper.visitDirectory(Path.of(toIoPath(dir)), filter, (file, ioAttributesHolder) -> {
        try {
          var attributes = amendAttributes(file, FileAttributes.fromNio(file, ioAttributesHolder.get()));
          childrenWithAttributes.put(file.getFileName().toString(), attributes);
        }
        catch (Exception e) { LOG.debug(e); }
        return true;
      });

      return childrenWithAttributes;
    }
    catch (AccessDeniedException | NoSuchFileException e) { LOG.debug(e); }
    catch (IOException | RuntimeException e) { LOG.warn(e); }
    return Map.of();
  }

  private static Object readContent(VirtualFile file) {
    try {
      var nioFile = Path.of(toIoPath(file));
      return readIfNotTooLarge(nioFile);
    }
    catch (IOException e) {
      return e;
    }
  }

  private static @Nullable FileAttributes readAttributes(VirtualFile file) {
    try {
      var nioFile = Path.of(toIoPath(file));
      var nioAttributes = Files.readAttributes(nioFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
      return amendAttributes(nioFile, FileAttributes.fromNio(nioFile, nioAttributes));
    }
    catch (AccessDeniedException | NoSuchFileException e) { LOG.debug(e); }
    catch (IOException | RuntimeException e) { LOG.warn(e); }
    return null;
  }

  private static FileAttributes amendAttributes(Path file, FileAttributes attributes) {
    for (var provider : LocalFileSystemTimestampEvaluator.EP_NAME.getExtensionList()) {
      var customTS = provider.getTimestamp(file);
      if (customTS != null) {
        return attributes.withTimeStamp(customTS);
      }
    }
    return attributes;
  }

  @Override
  public String toString() {
    return "LocalFileSystem";
  }
}
