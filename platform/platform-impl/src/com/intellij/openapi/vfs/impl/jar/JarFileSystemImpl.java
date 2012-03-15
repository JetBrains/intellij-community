/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.application.Application;
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
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.zip.ZipFile;

public class JarFileSystemImpl extends JarFileSystem implements ApplicationComponent {
  private final Set<String> myNoCopyJarPaths = new ConcurrentHashSet<String>();
  @NonNls private static final String IDEA_JARS_NOCOPY = "idea.jars.nocopy";
  private File myNoCopyJarDir;

  private final Map<String, JarHandler> myHandlers = new HashMap<String, JarHandler>();

  private static final class JarFileSystemImplLock {
  }

  private static final Object LOCK = new JarFileSystemImplLock();

  public JarFileSystemImpl(MessageBus bus) {
    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void before(@NotNull final List<? extends VFileEvent> events) {
      }

      @Override
      public void after(@NotNull final List<? extends VFileEvent> events) {
        final List<VirtualFile> rootsToRefresh = new ArrayList<VirtualFile>();

        for (VFileEvent event : events) {
          if (event.getFileSystem() instanceof LocalFileSystem) {
            final String path = event.getPath();
            List<String> jarPaths = new ArrayList<String>();
            synchronized (LOCK) {
              jarPaths.addAll(myHandlers.keySet());
            }

            for (String jarPath : jarPaths) {
              if (FileUtil.startsWith(jarPath.substring(0, jarPath.length() - JAR_SEPARATOR.length()),
                                      path,
                                      SystemInfo.isFileSystemCaseSensitive)) {
                VirtualFile jarRootToRefresh = markDirty(jarPath);
                if (jarRootToRefresh != null) {
                  rootsToRefresh.add(jarRootToRefresh);
                }
              }
            }
          }
        }

        if (!rootsToRefresh.isEmpty()) {
          final Application app = ApplicationManager.getApplication();
          Runnable runnable = new Runnable() {
            @Override
            public void run() {
              if (app.isDisposed()) return;
              for (VirtualFile root : rootsToRefresh) {
                if (root.isValid()) {
                  ((NewVirtualFile)root).markDirtyRecursively();
                }
              }

              VirtualFile[] roots = VfsUtil.toVirtualFileArray(rootsToRefresh);
              RefreshQueue.getInstance().refresh(false, true, null, roots);
            }
          };
          if (app.isUnitTestMode()) {
            runnable.run();
          }
          else {
            app.invokeLater(runnable, ModalityState.NON_MODAL);
          }
        }
      }
    });
  }

  @Nullable
  private VirtualFile markDirty(final String path) {
    final JarHandler handler;
    synchronized (LOCK) {
      handler = myHandlers.remove(path);
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
    //We want to prevent Platform from copying its own jars when running from dist to save system resources
    final boolean isRunningFromDist = new File(PathManager.getLibPath() + File.separatorChar + "openapi.jar").exists();
    if(isRunningFromDist) {
      myNoCopyJarDir = new File(new File(PathManager.getLibPath()).getParent());
    }
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public void setNoCopyJarForPath(String pathInJar) {
    int index = pathInJar.indexOf(JAR_SEPARATOR);
    if (index < 0) return;
    String path = pathInJar.substring(0, index);
    path = path.replace('/', File.separatorChar);
    if (!SystemInfo.isFileSystemCaseSensitive) {
      path = path.toLowerCase();
    }
    myNoCopyJarPaths.add(path);
  }

  @Override
  @Nullable
  public VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryVFile) {
    if (entryVFile == null) return null;
    final String path = entryVFile.getPath();
    final int separatorIndex = path.indexOf(JAR_SEPARATOR);
    if (separatorIndex < 0) return null;

    String localPath = path.substring(0, separatorIndex);
    return LocalFileSystem.getInstance().findFileByPath(localPath);
  }

  @Override
  public ZipFile getJarFile(VirtualFile entryVFile) throws IOException {
    JarHandler handler = getHandler(entryVFile);

    return handler.getZip();
  }

  @Nullable
  public File getMirroredFile(VirtualFile vFile) {
    VirtualFile jar = getJarRootForLocalFile(vFile);
    final JarHandler handler = jar != null ? getHandler(jar) : null;
    return handler != null ? handler.getMirrorFile(new File(vFile.getPath())) : null;
  }

  private JarHandler getHandler(final VirtualFile entryVFile) {
    final String jarRootPath = extractRootPath(entryVFile.getPath());

    JarHandler handler;
    final JarHandler freshHandler;

    synchronized (LOCK) {
      handler = myHandlers.get(jarRootPath);
      if (handler == null) {
        freshHandler = handler = new JarHandler(this, jarRootPath.substring(0, jarRootPath.length() - JAR_SEPARATOR.length()));
        myHandlers.put(jarRootPath, handler);
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

  @Override
  public String extractRootPath(@NotNull final String path) {
    final int jarSeparatorIndex = path.indexOf(JAR_SEPARATOR);
    assert jarSeparatorIndex >= 0 : "Path passed to JarFileSystem must have jar separator '!/': " + path;
    return path.substring(0, jarSeparatorIndex + JAR_SEPARATOR.length());
  }

  @Override
  public boolean isCaseSensitive() {
    return true;
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
    return getHandler(file).getOutputStream(file, requestor, modStamp, timeStamp);
  }

  public boolean isMakeCopyOfJar(File originalJar) {
    String property = System.getProperty(IDEA_JARS_NOCOPY);
    if (Boolean.TRUE.toString().equalsIgnoreCase(property)) return false;

    String path = originalJar.getPath();
    if (!SystemInfo.isFileSystemCaseSensitive) {
      path = path.toLowerCase();
    }

    if (myNoCopyJarPaths.contains(path)) return false;
    if (myNoCopyJarDir!=null && FileUtil.isAncestor(myNoCopyJarDir, originalJar, false)) return false;

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
  public void setTimeStamp(@NotNull final VirtualFile file, final long modstamp) throws IOException {
    getHandler(file).setTimeStamp(file, modstamp);
  }

  @Override
  public void setWritable(@NotNull final VirtualFile file, final boolean writableFlag) throws IOException {
    getHandler(file).setWritable(file, writableFlag);
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
  public VirtualFile copyFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent, @NotNull final String copyName) throws IOException {
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
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return VfsImplUtil.refreshAndFindFileByPath(this, path);
  }

  @Override
  public int getBooleanAttributes(@NotNull VirtualFile file, int flags) {
    int exists = 0;
    JarHandler handler = getHandler(file);
    if ((flags & FileUtil.BA_EXISTS) != 0) {
      exists = handler.exists(file) ? FileUtil.BA_EXISTS : 0;
    }
    int isDir = 0;
    if ((flags & FileUtil.BA_DIRECTORY) != 0) {
      isDir = handler.isDirectory(file) ? FileUtil.BA_DIRECTORY : 0;
    }
    int regular = 0;
    if ((flags & FileUtil.BA_REGULAR) != 0) {
      regular = isDir == 0 ? FileUtil.BA_REGULAR : 0;
    }
    return exists | isDir | regular;
  }

  @Override
  public FileAttributes getAttributes(@NotNull final VirtualFile file) {
    final JarHandler handler = getHandler(file);
    if (handler == null) return null;

    if (file.getParent() == null) {
      final LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
      final VirtualFile originalFile = localFileSystem.findFileByIoFile(handler.getOriginalFile());
      assert originalFile != null : file;
      return localFileSystem.getAttributes(originalFile);
    }

    return handler.getAttributes(file);
  }
}
