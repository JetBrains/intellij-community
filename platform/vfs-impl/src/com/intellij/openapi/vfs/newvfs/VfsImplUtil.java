// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileAttributes.CaseSensitivity;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static com.intellij.openapi.util.Pair.pair;

public final class VfsImplUtil {
  private static final Logger LOG = Logger.getInstance(VfsImplUtil.class);

  private static final String FILE_SEPARATORS = "/" + (File.separatorChar == '/' ? "" : File.separator);

  private VfsImplUtil() { }

  public static @Nullable NewVirtualFile findFileByPath(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    Pair<NewVirtualFile, Iterable<String>> data = prepare(vfs, path);
    if (data == null) return null;

    NewVirtualFile file = data.first;
    for (String pathElement : data.second) {
      if (pathElement.isEmpty() || ".".equals(pathElement)) continue;
      if ("..".equals(pathElement)) {
        if (file.is(VFileProperty.SYMLINK)) {
          final NewVirtualFile canonicalFile = file.getCanonicalFile();
          file = canonicalFile != null ? canonicalFile.getParent() : null;
        }
        else {
          file = file.getParent();
        }
      }
      else {
        file = file.findChild(pathElement);
      }

      if (file == null) return null;
    }

    return file;
  }

  public static @Nullable NewVirtualFile findFileByPathIfCached(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    return findCachedFileByPath(vfs, path).first;
  }

  public static @NotNull Pair<NewVirtualFile, NewVirtualFile> findCachedFileByPath(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    Pair<NewVirtualFile, Iterable<String>> data = prepare(vfs, path);
    if (data == null) return Pair.empty();

    NewVirtualFile file = data.first;
    for (String pathElement : data.second) {
      if (pathElement.isEmpty() || ".".equals(pathElement)) continue;

      NewVirtualFile last = file;
      if ("..".equals(pathElement)) {
        if (file.is(VFileProperty.SYMLINK)) {
          String canonicalPath = file.getCanonicalPath();
          NewVirtualFile canonicalFile = canonicalPath != null ? findCachedFileByPath(vfs, canonicalPath).first : null;
          file = canonicalFile != null ? canonicalFile.getParent() : null;
        }
        else {
          file = file.getParent();
        }
      }
      else {
        file = file.findChildIfCached(pathElement);
      }

      if (file == null) {
        return pair(null, last);
      }
    }

    return pair(file, null);
  }

  public static @Nullable NewVirtualFile refreshAndFindFileByPath(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    Pair<NewVirtualFile, Iterable<String>> data = prepare(vfs, path);
    if (data == null) return null;

    NewVirtualFile file = data.first;
    for (String pathElement : data.second) {
      if (pathElement.isEmpty() || ".".equals(pathElement)) continue;
      if ("..".equals(pathElement)) {
        if (file.is(VFileProperty.SYMLINK)) {
          final String canonicalPath = file.getCanonicalPath();
          final NewVirtualFile canonicalFile = canonicalPath != null ? refreshAndFindFileByPath(vfs, canonicalPath) : null;
          file = canonicalFile != null ? canonicalFile.getParent() : null;
        }
        else {
          file = file.getParent();
        }
      }
      else {
        file = file.refreshAndFindChild(pathElement);
      }

      if (file == null) return null;
    }

    return file;
  }

  private static @Nullable Pair<NewVirtualFile, Iterable<String>> prepare(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    Pair<NewVirtualFile, String> pair = extractRootFromPath(vfs, path);
    if (pair == null) return null;
    Iterable<String> parts = StringUtil.tokenize(pair.second, FILE_SEPARATORS);
    return pair(pair.first, parts);
  }

  public static Pair<NewVirtualFile, String> extractRootFromPath(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    String normalizedPath = vfs.normalize(path);
    if (normalizedPath == null || normalizedPath.isBlank()) {
      return null;
    }

    String basePath = vfs.extractRootPath(normalizedPath);
    if (basePath.isBlank() || basePath.length() > normalizedPath.length()) {
      LOG.warn(vfs + " has extracted incorrect root '" + basePath + "' from '" + normalizedPath + "' (original '" + path + "')");
      return null;
    }

    NewVirtualFile root = ManagingFS.getInstance().findRoot(basePath, vfs);
    if (root == null || !root.exists()) {
      return null;
    }

    return pair(root, normalizedPath.substring(basePath.length()));
  }

  public static void refresh(@NotNull NewVirtualFileSystem vfs, boolean asynchronous) {
    VirtualFile[] roots = ManagingFS.getInstance().getRoots(vfs);
    if (roots.length > 0) {
      RefreshQueue.getInstance().refresh(asynchronous, true, null, roots);
    }
  }

  /**
   * Guru method for force synchronous file refresh.
   * <p>
   * Refreshing files via {@link #refresh(NewVirtualFileSystem, boolean)} doesn't work well if the file was changed
   * twice in short time and content length wasn't changed (for example file modification timestamp for HFS+ works per seconds).
   * <p>
   * If you're sure that a file is changed twice in a second, and you have to get the latest file's state - use this method.
   * <p>
   * Likely you need this method if you have the following code:
   * <code><pre>
   * FileDocumentManager.getInstance().saveDocument(document);
   * runExternalToolToChangeFile(virtualFile.getPath()) // changes file externally in milliseconds, probably without changing file's length
   * VfsUtil.markDirtyAndRefresh(true, true, true, virtualFile); // might be replaced with forceSyncRefresh(virtualFile);
   * </pre></code>
   */
  public static void forceSyncRefresh(@NotNull VirtualFile file) {
    VFileContentChangeEvent event = new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, true);
    RefreshQueue.getInstance().processSingleEvent(false, event);
  }

  private static final AtomicBoolean ourSubscribed = new AtomicBoolean(false);
  private static final Object ourLock = new Object();
  private static final Map<String, Pair<ArchiveFileSystem, ArchiveHandler>> ourHandlerCache = CollectionFactory.createFilePathMap(); // guarded by ourLock
  private static final Map<String, Set<String>> ourDominatorsMap = CollectionFactory.createFilePathMap(); // guarded by ourLock; values too

  @ApiStatus.Internal
  public static @NotNull String getLocalPath(@NotNull ArchiveFileSystem vfs, @NotNull String entryPath) {
    return vfs.extractLocalPath(entryPath);
  }

  public static @NotNull <T extends ArchiveHandler> T getHandler(@NotNull ArchiveFileSystem vfs,
                                                                 @NotNull VirtualFile entryFile,
                                                                 @NotNull Function<? super String, ? extends T> producer) {
    String localPath = getLocalPath(vfs, VfsUtilCore.getRootFile(entryFile).getPath());
    checkSubscription();

    T handler;

    synchronized (ourLock) {
      Pair<ArchiveFileSystem, ArchiveHandler> record = ourHandlerCache.get(localPath);

      if (record == null) {
        handler = producer.fun(localPath);
        record = pair(vfs, handler);
        ourHandlerCache.put(localPath, record);

        forEachDirectoryComponent(localPath, containingDirectoryPath -> {
          Set<String> handlers = ourDominatorsMap.computeIfAbsent(containingDirectoryPath, __ -> new HashSet<>());
          handlers.add(localPath);
        });
      }

      @SuppressWarnings("unchecked") T t = (T)record.second;
      handler = t;
    }

    return handler;
  }

  private static void forEachDirectoryComponent(@NotNull String rootPath, @NotNull Consumer<? super String> consumer) {
    int index = rootPath.lastIndexOf('/');
    while (index > 0) {
      String containingDirectoryPath = rootPath.substring(0, index);
      consumer.accept(containingDirectoryPath);
      index = rootPath.lastIndexOf('/', index - 1);
    }
  }

  private static void checkSubscription() {
    if (ourSubscribed.getAndSet(true)) return;

    Application app = ApplicationManager.getApplication();
    if (app.isDisposed()) {
      // we might perform a shutdown activity that includes visiting archives (IDEA-181620)
      return;
    }
    MessageBusConnection connection = app.getMessageBus().connect(app);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        InvalidationState state = null;

        synchronized (ourLock) {
          for (VFileEvent event : events) {
            if (!(event.getFileSystem() instanceof LocalFileSystem)) continue;

            if (!(event instanceof VFileContentChangeEvent)) continue;

            String path = event.getPath();

            VirtualFile file = event.getFile();
            if (file == null || !file.isDirectory()) {
              state = invalidate(state, path);
            }
            else {
              Collection<String> affectedPaths = ourDominatorsMap.get(path);
              if (affectedPaths != null) {
                affectedPaths = new ArrayList<>(affectedPaths);  // defensive copying; original may be updated on invalidation
                for (String affectedPath : affectedPaths) {
                  state = invalidate(state, affectedPath);
                }
              }
            }
          }
        }

        if (state != null) state.scheduleRefresh();
      }
    });
    connection.subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void pluginUnloaded(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        synchronized (ourLock) {
          // avoid leaking ArchiveFileSystem registered by plugin, e.g. TgzFileSystem from Kubernetes plugin
          ourHandlerCache.clear();
        }
      }
    });
  }

  // must be called under ourLock
  private static @Nullable InvalidationState invalidate(@Nullable InvalidationState state, @NotNull String path) {
    Pair<ArchiveFileSystem, ArchiveHandler> handlerPair = ourHandlerCache.remove(path);
    if (handlerPair != null) {
      handlerPair.second.clearCaches();

      forEachDirectoryComponent(path, containingDirectoryPath -> {
        Set<String> handlers = ourDominatorsMap.get(containingDirectoryPath);
        if (handlers != null && handlers.remove(path) && handlers.isEmpty()) {
          ourDominatorsMap.remove(containingDirectoryPath);
        }
      });

      if (state == null) state = new InvalidationState();
      state.registerPathToRefresh(path, handlerPair.first);
    }

    return state;
  }

  /**
   * If the {@code parent} case-sensitivity flag is still not known, try to determine it via {@link FileSystemUtil#readParentCaseSensitivity(File)}.
   * If this flag read successfully, prepare to fire the {@link VirtualFile#PROP_CHILDREN_CASE_SENSITIVITY} event
   * (but only if this flag is different from the FS-default case-sensitivity to avoid too many unnecessary events:
   * see {@link VirtualFileSystem#isCaseSensitive()}).
   * Otherwise, return null.
   */
  public static VFilePropertyChangeEvent generateCaseSensitivityChangedEventForUnknownCase(@NotNull VirtualFile parent, @NotNull String childName) {
    if (((VirtualDirectoryImpl)parent).getChildrenCaseSensitivity() != CaseSensitivity.UNKNOWN) {
      return null;
    }
    CaseSensitivity sensitivity = FileSystemUtil.readParentCaseSensitivity(new File(parent.getPath(), childName));
    return generateCaseSensitivityChangedEvent(parent, sensitivity);
  }

  public static VFilePropertyChangeEvent generateCaseSensitivityChangedEvent(@NotNull VirtualFile dir, @NotNull CaseSensitivity actualCaseSensitivity) {
    if (actualCaseSensitivity != CaseSensitivity.UNKNOWN) {
      if (dir.getFileSystem().isCaseSensitive() != (actualCaseSensitivity == CaseSensitivity.SENSITIVE)) {
        // fire only when the new case sensitivity is different from the default FS sensitivity,
        // because only in that case the file.isCaseSensitive() value could change
        return new VFilePropertyChangeEvent(null, dir, VirtualFile.PROP_CHILDREN_CASE_SENSITIVITY, CaseSensitivity.UNKNOWN, actualCaseSensitivity, true);
      }
      else {
        PersistentFSImpl.executeChangeCaseSensitivity(dir, actualCaseSensitivity);
      }
    }
    return null;
  }

  private static class InvalidationState {
    private Set<Pair<String, ArchiveFileSystem>> myRootsToRefresh;

    private void registerPathToRefresh(@NotNull String path, @NotNull ArchiveFileSystem vfs) {
      if (myRootsToRefresh == null) myRootsToRefresh = new HashSet<>();
      myRootsToRefresh.add(pair(path, vfs));
    }

    private void scheduleRefresh() {
      if (myRootsToRefresh != null) {
        List<NewVirtualFile> rootsToRefresh = ContainerUtil.mapNotNull(myRootsToRefresh, pathAndFs ->
          ManagingFS.getInstance().findRoot(pathAndFs.second.composeRootPath(pathAndFs.first), pathAndFs.second));
        for (NewVirtualFile root : rootsToRefresh) {
          root.markDirtyRecursively();
        }
        boolean async = !ApplicationManager.getApplication().isUnitTestMode();
        RefreshQueue.getInstance().refresh(async, true, null, rootsToRefresh);
      }
    }
  }

  @TestOnly
  public static void releaseHandler(@NotNull String localPath) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) throw new IllegalStateException();
    synchronized (ourLock) {
      InvalidationState state = invalidate(null, localPath);
      if (state == null) throw new IllegalArgumentException(localPath + " not in " + ourHandlerCache.keySet());
    }
  }

  /**
   * <p>Checks whether the {@code event} (in {@link LocalFileSystem}) affects some archives and if so,
   * generates appropriate additional {@link JarFileSystem}-events and corresponding after-event-actions.</p>
   * <p>For example, {@link VFileDeleteEvent}/{@link VFileMoveEvent}/{@link VFilePropertyChangeEvent VFilePropertyChangeEvent(PROP_NAME)}('file://tmp/x.jar')
   * should generate {@link VFileDeleteEvent}('jar:///tmp/x.jar!/').</p>
   * And vice versa, when refresh found change inside jar archive, generate {@link LocalFileSystem}-level events
   * for the corresponding .jar file change.
   * <p>For example, {@link VFileDeleteEvent}/{@link VFileMoveEvent}/{@link VFilePropertyChangeEvent VFilePropertyChangeEvent(PROP_NAME)}('jar:///x.jar!/')
   * should generate {@link VFileDeleteEvent}('file://x.jar').</p>
   * (The latter might happen when someone explicitly called {@code fileInsideJar.refresh()} without refreshing jar file in local file system).
   */
  public static @NotNull List<VFileEvent> getJarInvalidationEvents(@NotNull VFileEvent event, @NotNull List<? super Runnable> outApplyActions) {
    if (!(event instanceof VFileDeleteEvent ||
          event instanceof VFileMoveEvent ||
          event instanceof VFilePropertyChangeEvent && VirtualFile.PROP_NAME.equals(((VFilePropertyChangeEvent)event).getPropertyName()))) {
      return Collections.emptyList();
    }

    String path;
    if (event instanceof VFilePropertyChangeEvent) {
      path = ((VFilePropertyChangeEvent)event).getOldPath();
    }
    else if (event instanceof VFileMoveEvent) {
      path = ((VFileMoveEvent)event).getOldPath();
    }
    else {
      path = event.getPath();
    }

    VirtualFile file = event.getFile();
    if (file == null) {
      return Collections.emptyList();
    }
    VirtualFileSystem entryFileSystem = file.getFileSystem();
    VirtualFile local = null;
    if (entryFileSystem instanceof ArchiveFileSystem) {
      local = ((ArchiveFileSystem)entryFileSystem).getLocalByEntry(file);
      path = local == null ? getLocalPath((ArchiveFileSystem)entryFileSystem, path) : local.getPath();
    }
    String[] jarPaths;
    synchronized (ourLock) {
      Set<String> handlers = ourDominatorsMap.get(path);
      if (handlers == null) {
        jarPaths = new String[] {path};
      }
      else {
        jarPaths = ArrayUtil.toStringArray(handlers);
      }
    }
    List<VFileEvent> events = new ArrayList<>(jarPaths.length);
    for (String jarPath : jarPaths) {
      Pair<ArchiveFileSystem, ArchiveHandler> handlerPair = ourHandlerCache.get(jarPath);
      if (handlerPair == null) {
        continue;
      }
      if (entryFileSystem instanceof LocalFileSystem) {
        ArchiveFileSystem fileSystem = handlerPair.first;
        NewVirtualFile root = ManagingFS.getInstance().findRoot(fileSystem.composeRootPath(jarPath), fileSystem);
        if (root != null) {
          VFileDeleteEvent jarDeleteEvent = new VFileDeleteEvent(event.getRequestor(), root, event.isFromRefresh());
          Runnable runnable = () -> {
            Pair<ArchiveFileSystem, ArchiveHandler> pair = ourHandlerCache.remove(jarPath);
            if (pair != null) {
              pair.second.clearCaches();
              synchronized (ourLock) {
                forEachDirectoryComponent(jarPath, containingDirectoryPath -> {
                  Set<String> handlers = ourDominatorsMap.get(containingDirectoryPath);
                  if (handlers != null && handlers.remove(jarPath) && handlers.isEmpty()) {
                    ourDominatorsMap.remove(containingDirectoryPath);
                  }
                });
              }
            }
          };
          events.add(jarDeleteEvent);
          outApplyActions.add(runnable);
        }
      }
      else if (local != null) {
        // for "delete jar://x.jar!/" generate "delete file://x.jar", but
        // for "delete jar://x.jar!/web.xml" generate "changed file://x.jar"
        VFileEvent localJarDeleteEvent = file.getParent() == null ?
           new VFileDeleteEvent(event.getRequestor(), local, event.isFromRefresh()) :
           new VFileContentChangeEvent(event.getRequestor(), local, local.getModificationStamp(), local.getModificationStamp(), event.isFromRefresh());
        events.add(localJarDeleteEvent);
      }
    }
    return events;
  }

  @ApiStatus.Internal
  public static @NotNull String getRecordPath(int record) {
    StringBuilder name = new StringBuilder(FSRecords.getName(record));
    int parent = FSRecords.getParent(record);
    while (parent > 0) {
      name.insert(0, FSRecords.getName(parent) + "/");
      parent = FSRecords.getParent(parent);
    }
    return name.toString();
  }

  @ApiStatus.Internal
  public static String getRecordIdPath(int record) {
    StringBuilder name = new StringBuilder(String.valueOf(record));
    int parent = FSRecords.getParent(record);
    while (parent > 0) {
      name.insert(0, parent + " -> ");
      parent = FSRecords.getParent(parent);
    }
    return name.toString();
  }
}
