/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.messages.MessageBus;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JarFileSystemImpl extends JarFileSystem implements ApplicationComponent {
  private static final class JarFileSystemImplLock { }
  private static final JarFileSystemImplLock LOCK = new JarFileSystemImplLock();

  private final Set<String> myNoCopyJarPaths;
  private File myNoCopyJarDir;
  private final Map<String, JarHandler> myHandlers = new THashMap<String, JarHandler>(FileUtil.PATH_HASHING_STRATEGY);
  private String[] jarPathsCache;

  public JarFileSystemImpl(MessageBus bus) {
    boolean noCopy = SystemProperties.getBooleanProperty("idea.jars.nocopy", !SystemInfo.isWindows);
    myNoCopyJarPaths = noCopy ? null : new ConcurrentHashSet<String>(FileUtil.PATH_HASHING_STRATEGY);

    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener.Adapter() {
      @Override
      public void after(@NotNull final List<? extends VFileEvent> events) {
        final List<VirtualFile> rootsToRefresh = new ArrayList<VirtualFile>();

        for (VFileEvent event : events) {
          if (event.getFileSystem() instanceof LocalFileSystem) {
            String path = event.getPath();

            String[] jarPaths;
            synchronized (LOCK) {
              jarPaths = jarPathsCache;
              if (jarPaths == null) {
                jarPathsCache = jarPaths = ArrayUtil.toStringArray(myHandlers.keySet());
              }
            }

            for (String jarPath : jarPaths) {
              final String jarFile = jarPath.substring(0, jarPath.length() - JAR_SEPARATOR.length());
              if (FileUtil.startsWith(jarFile, path)) {
                VirtualFile jarRootToRefresh = markDirty(jarPath);
                if (jarRootToRefresh != null) {
                  rootsToRefresh.add(jarRootToRefresh);
                }
              }
            }
          }
        }

        if (!rootsToRefresh.isEmpty()) {
          for (VirtualFile root : rootsToRefresh) {
            if (root.isValid()) {
              ((NewVirtualFile)root).markDirtyRecursively();
            }
          }
          RefreshQueue.getInstance().refresh(!ApplicationManager.getApplication().isUnitTestMode(), true, null, rootsToRefresh);
        }
      }
    });
  }

  @Nullable
  private VirtualFile markDirty(@NotNull String path) {
    final JarHandler handler;
    synchronized (LOCK) {
      handler = myHandlers.remove(path);
      jarPathsCache = null;
    }

    if (handler != null) {
      return handler.markDirty();
    }

    return null;
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "JarFileSystem";
  }

  @Override
  public void initComponent() {
    // we want to prevent Platform from copying its own jars when running from dist to save system resources
    final boolean isRunningFromDist = new File(PathManager.getLibPath() + File.separatorChar + "openapi.jar").exists();
    if (isRunningFromDist) {
      myNoCopyJarDir = new File(new File(PathManager.getLibPath()).getParent());
    }
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void setNoCopyJarForPath(String pathInJar) {
    if (myNoCopyJarPaths == null || pathInJar == null) {
      return;
    }
    int index = pathInJar.indexOf(JAR_SEPARATOR);
    if (index < 0) return;
    String path = pathInJar.substring(0, index);
    path = path.replace('/', File.separatorChar);
    myNoCopyJarPaths.add(path);
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryVFile) {
    return StandardFileSystems.getVirtualFileForJar(entryVFile);
  }

  @Override
  public JarFile getJarFile(@NotNull VirtualFile entryVFile) throws IOException {
    JarHandler handler = getHandler(entryVFile);

    return handler.getJar();
  }

  @Nullable
  public File getMirroredFile(@NotNull VirtualFile vFile) {
    VirtualFile jar = getJarRootForLocalFile(vFile);
    final JarHandler handler = jar != null ? getHandler(jar) : null;
    return handler != null ? handler.getMirrorFile(new File(vFile.getPath())) : null;
  }

  private JarHandler getHandler(@NotNull VirtualFile entryVFile) {
    final String jarRootPath = extractRootPath(entryVFile.getPath());

    JarHandler handler;
    final JarHandler freshHandler;

    synchronized (LOCK) {
      handler = myHandlers.get(jarRootPath);
      if (handler == null) {
        freshHandler = handler = new JarHandler(this, jarRootPath.substring(0, jarRootPath.length() - JAR_SEPARATOR.length()));
        myHandlers.put(jarRootPath, handler);
        jarPathsCache = null;
      }
      else {
        freshHandler = null;
      }
    }

    if (freshHandler != null) {
      // Refresh must be outside of the lock, since it potentially requires write action.
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          freshHandler.refreshLocalFileForJar();
        }
      }, ModalityState.defaultModalityState());
    }

    return handler;
  }

  @Override
  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  @NotNull
  @Override
  public String extractPresentableUrl(@NotNull String path) {
    return super.extractPresentableUrl(StringUtil.trimEnd(path, JAR_SEPARATOR));
  }

  @NotNull
  @Override
  protected String extractRootPath(@NotNull final String path) {
    final int jarSeparatorIndex = path.indexOf(JAR_SEPARATOR);
    assert jarSeparatorIndex >= 0 : "Path passed to JarFileSystem must have jar separator '!/': " + path;
    return path.substring(0, jarSeparatorIndex + JAR_SEPARATOR.length());
  }

  @Override
  public boolean exists(@NotNull final VirtualFile fileOrDirectory) {
    return getHandler(fileOrDirectory).exists(fileOrDirectory);
  }

  @Override
  @NotNull
  public InputStream getInputStream(@NotNull final VirtualFile file) throws IOException {
    return getHandler(file).getInputStream(file);
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray(@NotNull final VirtualFile file) throws IOException {
    return getHandler(file).contentsToByteArray(file);
  }

  @Override
  public long getLength(@NotNull final VirtualFile file) {
    return getHandler(file).getLength(file);
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(@NotNull final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp)
    throws IOException {
    throw new IOException("Read-only: "+file.getPresentableUrl());
  }

  public boolean isMakeCopyOfJar(@NotNull File originalJar) {
    if (myNoCopyJarPaths == null || myNoCopyJarPaths.contains(originalJar.getPath())) return false;
    if (myNoCopyJarDir != null && FileUtil.isAncestor(myNoCopyJarDir, originalJar, false)) return false;
    return true;
  }

  @Override
  public long getTimeStamp(@NotNull final VirtualFile file) {
    return getHandler(file).getTimeStamp(file);
  }

  @Override
  public boolean isDirectory(@NotNull final VirtualFile file) {
    return getHandler(file).isDirectory(file);
  }

  @Override
  public boolean isWritable(@NotNull final VirtualFile file) {
    return false;
  }

  @Override
  @NotNull
  public String[] list(@NotNull final VirtualFile file) {
    return getHandler(file).list(file);
  }

  @Override
  public void setTimeStamp(@NotNull final VirtualFile file, final long modStamp) throws IOException {
    throw new IOException("Read-only: "+file.getPresentableUrl());
  }

  @Override
  public void setWritable(@NotNull final VirtualFile file, final boolean writableFlag) throws IOException {
    throw new IOException("Read-only: "+file.getPresentableUrl());
  }

  @Override
  @NotNull
  public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vDir.getUrl()));
  }

  @Override
  public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vDir.getUrl()));
  }

  @Override
  public void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
  }

  @Override
  public void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vFile.getUrl()));
  }

  @Override
  public VirtualFile copyFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent, @NotNull String copyName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vFile.getUrl()));
  }

  @Override
  public void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vFile.getUrl()));
  }

  @Override
  public int getRank() {
    return 2;
  }

  @Override
  public void refresh(final boolean asynchronous) {
    RefreshQueue.getInstance().refresh(asynchronous, true, null, ManagingFS.getInstance().getRoots(this));
  }

  @Override
  public VirtualFile findFileByPath(@NotNull @NonNls String path) {
    return VfsImplUtil.findFileByPath(this, path);
  }

  @Override
  public VirtualFile findFileByPathIfCached(@NotNull @NonNls String path) {
    return VfsImplUtil.findFileByPathIfCached(this, path);
  }

  @Override
  protected String normalize(@NotNull String path) {
    final int jarSeparatorIndex = path.indexOf(JAR_SEPARATOR);
    if (jarSeparatorIndex > 0) {
      final String root = path.substring(0, jarSeparatorIndex);
      return FileUtil.normalize(root) + path.substring(jarSeparatorIndex);
    }
    return super.normalize(path);
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return VfsImplUtil.refreshAndFindFileByPath(this, path);
  }

  @Override
  public FileAttributes getAttributes(@NotNull final VirtualFile file) {
    final JarHandler handler = getHandler(file);
    if (handler == null) return null;

    if (file.getParent() == null) {
      final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
      final VirtualFile originalFile = localFileSystem.findFileByIoFile(handler.getOriginalFile());
      if (originalFile == null) return null;
      final FileAttributes attributes = localFileSystem.getAttributes(originalFile);
      if (attributes == null) return null;
      return new FileAttributes(true, false, false, false, attributes.length, attributes.lastModified, attributes.isWritable());
    }

    return handler.getAttributes(file);
  }
}
