// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vfs.DiskQueryRelay;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFilePointerCapableFileSystem;
import com.intellij.openapi.vfs.newvfs.FileNavigator;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.openapi.vfs.newvfs.persistent.BatchingFileSystem;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.PlatformNioHelper;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import static com.intellij.openapi.vfs.impl.local.LocalFileSystemEelUtil.listWithAttributesUsingEel;
import static com.intellij.openapi.vfs.impl.local.LocalFileSystemEelUtil.readAttributesUsingEel;
import static com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl.createCreateEvent;
import static com.intellij.util.containers.CollectionFactory.createFilePathMap;
import static java.util.Objects.requireNonNullElse;

@ApiStatus.Internal
@SuppressWarnings("removal")
public class LocalFileSystemImpl
  extends LocalFileSystemBase
  implements Disposable, BatchingFileSystem, VirtualFilePointerCapableFileSystem
{
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
    new DiskQueryRelay<>(pair -> listWithAttributesUsingEel(pair.first, pair.second));

  private final FileNavigator<NewVirtualFile> NON_REFRESHING_NAVIGATOR = new FileNavigator<>() {
    @Override
    public @Nullable NewVirtualFile parentOf(@NotNull NewVirtualFile file) {
      // copied from VfsImplUtil.refreshAndFindFileByPath
      if (!file.is(VFileProperty.SYMLINK)) {
        return file.getParent();
      }
      var canonicalPath = file.getCanonicalPath();
      return canonicalPath != null ? VfsImplUtil.refreshAndFindFileByPath(LocalFileSystemImpl.this, canonicalPath) : null;
    }

    @Override
    public @Nullable NewVirtualFile childOf(@NotNull NewVirtualFile parent, @NotNull String childName) {
      return parent.findChild(childName);
    }
  };

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
    aliases.addFirst(path);
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
      for (var root : myManagingFS.getRoots(this)) {
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
  public void refreshNioFiles(@NotNull Iterable<? extends Path> files, boolean async, boolean recursive, @Nullable Runnable onFinish) {
    refreshNioFilesInternal(files);
    refreshFiles(ContainerUtil.mapNotNull(files, this::findFileByNioFile), async, recursive, onFinish);
  }

  public void refreshNioFilesInternal(@NotNull Iterable<? extends Path> files) {
    // simulate logic in VirtualDirectoryImpl.findChild but for all files at once
    List<VFileCreateEvent> createEventsToFire = new ArrayList<>();
    for (var file : files) {
      var result = FileNavigator.navigate(this, file.toAbsolutePath().toString(), NON_REFRESHING_NAVIGATOR);
      if (result.isResolved()) {
        continue;
      }
      var lastResolvedFile = result.lastResolvedFile();
      var nextChild = result.getUnresolvedChildName();
      if (lastResolvedFile != null && nextChild != null) {
        var fake = new FakeVirtualFile(lastResolvedFile, nextChild);
        var canonicallyCasedName = this.getCanonicallyCasedName(fake);
        var event = createCreateEvent(lastResolvedFile, fake, canonicallyCasedName, this);
        if (event != null) {
          // file exists on disk
          createEventsToFire.add(event);
        }
      }
    }
    if (!createEventsToFire.isEmpty()) {
      RefreshQueue.getInstance().processEvents(/*async: */ false, createEventsToFire);
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
    if (!(parent instanceof VirtualFileSystemEntry p)) return false;
    // check if it's circular - any symlink above resolves to my target too
    for (; p != null; p = p.getParent()) {
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
    return file.isDirectory() ? myChildrenGetter.accessDiskWithCheckCanceled(file) : ArrayUtil.EMPTY_STRING_ARRAY;
  }

  private final DiskQueryRelay<Pair<VirtualFile, String>, FileAttributes.CaseSensitivity> caseSensitivityGetter = new DiskQueryRelay<>(
    it -> super.fetchCaseSensitivity(it.first, it.second)
  );

  @Override
  public @NotNull FileAttributes.CaseSensitivity fetchCaseSensitivity(@NotNull VirtualFile parent, @NotNull String childName) {
    return caseSensitivityGetter.accessDiskWithCheckCanceled(Pair.createNonNull(parent, childName));
  }

  @Override
  public byte @NotNull [] contentsToByteArray(@NotNull VirtualFile file) throws IOException {
    //if (SystemInfo.isUnix && file.is(VFileProperty.SPECIAL)) { // avoid opening FIFO files
    //  throw new NoSuchFileException(file.getPath(), null, "Not a file");
    //}
    var result = myContentGetter.accessDiskWithCheckCanceled(file);
    if (result instanceof IOException e) throw e;
    return (byte[])result;
  }

  @Override
  public FileAttributes getAttributes(@NotNull VirtualFile file) {
    if (OS.CURRENT == OS.Windows && file.getParent() == null && file.getPath().startsWith("//")) {
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
  public @NotNull Map<@NotNull String, @NotNull FileAttributes> listWithAttributes(@NotNull VirtualFile dir, @Nullable Set<String> childrenNames) {
    if (!dir.isDirectory()) {
      return Collections.emptyMap();
    }
    return myChildrenAttrGetter.accessDiskWithCheckCanceled(new Pair<>(dir, childrenNames));
  }

  protected static Map<String, FileAttributes> listWithAttributesImpl(@NotNull Path dir, @Nullable Set<String> filter) {
    try {
      var expectedSize = (filter == null) ? 10 : filter.size();
      //We must return a 'normal' (=case-sensitive) map from this method, see BatchingFileSystem.listWithAttributes() contract:
      Map<String, FileAttributes> childrenWithAttributes = createFilePathMap(expectedSize, /*caseSensitive: */true);

      PlatformNioHelper.visitDirectory(dir, filter, (file, ioAttributesHolder) -> {
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
      var nioPath = Path.of(toIoPath(file));
      checkNotSpecialFile(file, nioPath);
      return readIfNotTooLarge(nioPath);
    }
    catch (IOException e) {
      return e;
    }
  }

  private static @Nullable FileAttributes readAttributes(VirtualFile file) {
    try {
      var nioFile = Path.of(toIoPath(file));
      var attributes = readAttributesUsingEel(nioFile);
      return amendAttributes(nioFile, attributes);
    }
    catch (NoSuchFileException e) { LOG.debug("File doesn't exist: " + e.getMessage()); }
    catch (AccessDeniedException e) { LOG.debug(e); }
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
