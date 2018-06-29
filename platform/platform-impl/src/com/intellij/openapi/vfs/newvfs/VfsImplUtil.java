// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class VfsImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.VfsImplUtil");

  private static final String FILE_SEPARATORS = "/" + File.separator;

  private VfsImplUtil() { }

  @Nullable
  public static NewVirtualFile findFileByPath(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
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

  @Nullable
  public static NewVirtualFile findFileByPathIfCached(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    return findCachedFileByPath(vfs, path).first;
  }

  @NotNull
  public static Pair<NewVirtualFile, NewVirtualFile> findCachedFileByPath(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
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

      if (file == null) return Pair.pair(null, last);
    }

    return Pair.pair(file, null);
  }

  @Nullable
  public static NewVirtualFile refreshAndFindFileByPath(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
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

  @Nullable
  private static Pair<NewVirtualFile, Iterable<String>> prepare(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    String normalizedPath = normalize(vfs, path);
    if (StringUtil.isEmptyOrSpaces(normalizedPath)) {
      return null;
    }

    String basePath = vfs.extractRootPath(normalizedPath);
    if (basePath.length() > normalizedPath.length() || basePath.isEmpty()) {
      LOG.warn(vfs + " failed to extract root path '" + basePath + "' from '" + normalizedPath + "' (original '" + path + "')");
      return null;
    }

    NewVirtualFile root = ManagingFS.getInstance().findRoot(basePath, vfs);
    if (root == null || !root.exists()) {
      return null;
    }

    Iterable<String> parts = StringUtil.tokenize(normalizedPath.substring(basePath.length()), FILE_SEPARATORS);
    return Pair.create(root, parts);
  }

  public static void refresh(@NotNull NewVirtualFileSystem vfs, boolean asynchronous) {
    VirtualFile[] roots = ManagingFS.getInstance().getRoots(vfs);
    if (roots.length > 0) {
      RefreshQueue.getInstance().refresh(asynchronous, true, null, roots);
    }
  }

  public static String normalize(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    return vfs.normalize(path);
  }

  /**
   * Guru method for force synchronous file refresh.
   *
   * Refreshing files via {@link #refresh(NewVirtualFileSystem, boolean)} doesn't work well if the file was changed
   * twice in short time and content length wasn't changed (for example file modification timestamp for HFS+ works per seconds).
   *
   * If you're sure that a file is changed twice in a second and you have to get the latest file's state - use this method.
   *
   * Likely you need this method if you have following code:
   *
   * <code>
   *  FileDocumentManager.getInstance().saveDocument(document);
   *  runExternalToolToChangeFile(virtualFile.getPath()) // changes file externally in milliseconds, probably without changing file's length
   *  VfsUtil.markDirtyAndRefresh(true, true, true, virtualFile); // might be replace with {@link #forceSyncRefresh(VirtualFile)}
   * </code>
   */
  public static void forceSyncRefresh(@NotNull VirtualFile file) {
    RefreshQueue.getInstance().processSingleEvent(new VFileContentChangeEvent(null, file, file.getModificationStamp(), -1, true));
  }

  private static final AtomicBoolean ourSubscribed = new AtomicBoolean(false);
  private static final Object ourLock = new Object();
  private static final Map<String, Pair<ArchiveFileSystem, ArchiveHandler>> ourHandlers = ContainerUtil.newTroveMap(FileUtil.PATH_HASHING_STRATEGY);
  private static final Map<String, Set<String>> ourDominatorsMap = ContainerUtil.newTroveMap(FileUtil.PATH_HASHING_STRATEGY);

  @NotNull
  public static <T extends ArchiveHandler> T getHandler(@NotNull ArchiveFileSystem vfs,
                                                        @NotNull VirtualFile entryFile,
                                                        @NotNull Function<String, T> producer) {
    String localPath = vfs.extractLocalPath(vfs.extractRootPath(entryFile.getPath()));
    checkSubscription();

    T handler;

    synchronized (ourLock) {
      Pair<ArchiveFileSystem, ArchiveHandler> record = ourHandlers.get(localPath);

      if (record == null) {
        handler = producer.fun(localPath);
        record = Pair.create(vfs, handler);
        ourHandlers.put(localPath, record);

        forEachDirectoryComponent(localPath, containingDirectoryPath -> {
          Set<String> handlers = ourDominatorsMap.computeIfAbsent(containingDirectoryPath, __ -> ContainerUtil.newTroveSet());
          handlers.add(localPath);
        });
      }

      @SuppressWarnings("unchecked") T t = (T)record.second;
      handler = t;
    }

    return handler;
  }

  private static void forEachDirectoryComponent(String rootPath, Consumer<String> consumer) {
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
    if (app.isDisposeInProgress()) return;  // we might perform a shutdown activity that includes visiting archives (IDEA-181620)
    app.getMessageBus().connect(app).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        InvalidationState state = null;

        synchronized (ourLock) {
          for (VFileEvent event : events) {
            if (!(event.getFileSystem() instanceof LocalFileSystem)) continue;

            if (event instanceof VFileCreateEvent) continue;  // new files don't affect existing handlers (and getFile() is costly)

            if (event instanceof VFilePropertyChangeEvent &&
                !VirtualFile.PROP_NAME.equals(((VFilePropertyChangeEvent)event).getPropertyName())) {
              continue;
            }

            String path = event.getPath();
            if (event instanceof VFilePropertyChangeEvent) {
              path = ((VFilePropertyChangeEvent)event).getOldPath();
            }
            else if (event instanceof VFileMoveEvent) {
              path = ((VFileMoveEvent)event).getOldPath();
            }

            VirtualFile file = event.getFile();
            if (file == null || !file.isDirectory()) {
              state = invalidate(state, path);
            }
            else {
              Collection<String> affectedPaths = ourDominatorsMap.get(path);
              if (affectedPaths != null) {
                affectedPaths = ContainerUtil.newArrayList(affectedPaths);  // defensive copying; original may be updated on invalidation
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
  }

  @Nullable
  private static InvalidationState invalidate(@Nullable InvalidationState state, String path) {
    Pair<ArchiveFileSystem, ArchiveHandler> handlerPair = ourHandlers.remove(path);
    if (handlerPair != null) {
      handlerPair.second.dispose();

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

  private static class InvalidationState {
    private Set<NewVirtualFile> myRootsToRefresh;

    private void registerPathToRefresh(String path, ArchiveFileSystem vfs) {
      NewVirtualFile root = ManagingFS.getInstance().findRoot(vfs.composeRootPath(path), vfs);
      if (root != null) {
        if (myRootsToRefresh == null) myRootsToRefresh = ContainerUtil.newHashSet();
        myRootsToRefresh.add(root);
      }
    }

    private void scheduleRefresh() {
      if (myRootsToRefresh != null) {
        for (NewVirtualFile root : myRootsToRefresh) {
          root.markDirtyRecursively();
        }
        boolean async = !ApplicationManager.getApplication().isUnitTestMode();
        RefreshQueue.getInstance().refresh(async, true, null, myRootsToRefresh);
      }
    }
  }
}