/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.ZipFileCache;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.ArchiveHandler;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class VfsImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.VfsImplUtil");

  private static final String FILE_SEPARATORS = "/" + File.separator;

  private VfsImplUtil() { }

  @Nullable
  public static NewVirtualFile findFileByPath(@NotNull NewVirtualFileSystem vfs, @NotNull @NonNls String path) {
    Pair<NewVirtualFile, Iterable<String>> data = prepare(vfs, path);
    if (data == null) {
      return null;
    }

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
  public static NewVirtualFile findFileByPathIfCached(@NotNull NewVirtualFileSystem vfs, @NotNull @NonNls String path) {
    Pair<NewVirtualFile, Iterable<String>> data = prepare(vfs, path);
    if (data == null) {
      return null;
    }

    NewVirtualFile file = data.first;
    for (String pathElement : data.second) {
      if (pathElement.isEmpty() || ".".equals(pathElement)) continue;
      if ("..".equals(pathElement)) {
        if (file.is(VFileProperty.SYMLINK)) {
          final String canonicalPath = file.getCanonicalPath();
          final NewVirtualFile canonicalFile = canonicalPath != null ? findFileByPathIfCached(vfs, canonicalPath) : null;
          file = canonicalFile != null ? canonicalFile.getParent() : null;
        }
        else {
          file = file.getParent();
        }
      }
      else {
        file = file.findChildIfCached(pathElement);
      }

      if (file == null) return null;
    }

    return file;
  }

  @Nullable
  public static NewVirtualFile refreshAndFindFileByPath(@NotNull NewVirtualFileSystem vfs, @NotNull @NonNls String path) {
    Pair<NewVirtualFile, Iterable<String>> data = prepare(vfs, path);
    if (data == null) {
      return null;
    }

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
    if (basePath.length() > normalizedPath.length()) {
      LOG.error(vfs + " failed to extract root path '" + basePath + "' from '" + normalizedPath + "' (original '" + path + "')");
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

  @Nullable
  public static String normalize(@NotNull NewVirtualFileSystem vfs, @NotNull String path) {
    return vfs.normalize(path);
  }

  private static final AtomicBoolean ourSubscribed = new AtomicBoolean(false);
  private static final Object ourLock = new Object();
  private static final Map<String, Pair<ArchiveFileSystem, ArchiveHandler>> ourHandlers = ContainerUtil.newTroveMap(FileUtil.PATH_HASHING_STRATEGY);

  @NotNull
  public static <T extends ArchiveHandler> T getHandler(@NotNull VirtualFile entryFile,
                                                        @NotNull ArchiveFileSystem vfs,
                                                        @NotNull Function<String, T> producer) {
    checkSubscription();

    String rootPath = vfs.extractRootPath(entryFile.getPath());
    ArchiveHandler handler;
    boolean refresh = false;

    synchronized (ourLock) {
      Pair<ArchiveFileSystem, ArchiveHandler> record = ourHandlers.get(rootPath);
      if (record == null) {
        handler = producer.fun(rootPath);
        record = Pair.create(vfs, handler);
        ourHandlers.put(rootPath, record);
        refresh = true;
      }
      handler = record.second;
    }

    if (refresh) {
      final File file = handler.getFile();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        }
      }, ModalityState.defaultModalityState());
    }

    @SuppressWarnings("unchecked") T t = (T)handler;
    return t;
  }

  private static void checkSubscription() {
    if (ourSubscribed.getAndSet(true)) return;

    MessageBus bus = ApplicationManager.getApplication().getMessageBus();
    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull List<? extends VFileEvent> events) {
        Map<String, VirtualFile> rootsToRefresh = null;

        synchronized (ourLock) {
          String[] rootPaths = ArrayUtil.toStringArray(ourHandlers.keySet());

          for (VFileEvent event : events) {
            if (!(event.getFileSystem() instanceof LocalFileSystem)) continue;

            String path = event.getPath();
            for (int i = 0; i < rootPaths.length; i++) {
              String rootPath = rootPaths[i];
              if (rootPath == null) continue;

              ArchiveFileSystem vfs = ourHandlers.get(rootPath).first;
              String localPath = vfs.extractLocalPath(rootPath);
              if (FileUtil.startsWith(localPath, path)) {
                ourHandlers.remove(rootPath);
                NewVirtualFile root = ManagingFS.getInstance().findRoot(rootPath, vfs);
                if (root != null) {
                  root.markDirtyRecursively();
                  if (rootsToRefresh == null) rootsToRefresh = ContainerUtil.newHashMap();
                  rootsToRefresh.put(localPath, root);
                }
                rootPaths[i] = null;
              }
            }
          }
        }

        if (rootsToRefresh != null) {
          ZipFileCache.reset(rootsToRefresh.keySet());
          boolean async = !ApplicationManager.getApplication().isUnitTestMode();
          RefreshQueue.getInstance().refresh(async, true, null, rootsToRefresh.values());
        }
      }
    });
  }
}
