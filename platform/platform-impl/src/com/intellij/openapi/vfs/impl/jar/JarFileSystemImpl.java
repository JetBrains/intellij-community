/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
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
      public void before(final List<? extends VFileEvent> events) {
      }

      public void after(final List<? extends VFileEvent> events) {
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

  @NotNull
  public String getComponentName() {
    return "JarFileSystem";
  }

  public void initComponent() {
    //We want to prevent Platform from copying its own jars when running from dist to save system resources
    final boolean isRunningFromDist = new File(PathManager.getLibPath() + File.separatorChar + "openapi.jar").exists();
    if(isRunningFromDist) {
      myNoCopyJarDir = new File(new File(PathManager.getLibPath()).getParent());
    }
  }

  public void disposeComponent() {
  }

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

  @Nullable
  public VirtualFile getVirtualFileForJar(VirtualFile entryVFile) {
    if (entryVFile == null) return null;
    final String path = entryVFile.getPath();
    final int separatorIndex = path.indexOf(JAR_SEPARATOR);
    if (separatorIndex < 0) return null;

    String localPath = path.substring(0, separatorIndex);
    return LocalFileSystem.getInstance().findFileByPath(localPath);
  }

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
    final JarHandler freshHanlder;

    synchronized (LOCK) {
      handler = myHandlers.get(jarRootPath);
      if (handler == null) {
        freshHanlder = handler = new JarHandler(this, jarRootPath.substring(0, jarRootPath.length() - JAR_SEPARATOR.length()));
        myHandlers.put(jarRootPath, handler);
      }
      else {
        freshHanlder = null;
      }
    }

    if (freshHanlder != null) {
      // Refresh must be outside of the lock, since it potentially requires write action.
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          freshHanlder.refreshLocalFileForJar();
        }
      }, ModalityState.defaultModalityState());
    }

    return handler;
  }

  @NotNull
  public String getProtocol() {
    return PROTOCOL;
  }

  public String extractPresentableUrl(@NotNull String path) {
    if (path.endsWith(JAR_SEPARATOR)) {
      path = path.substring(0, path.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    path = super.extractPresentableUrl(path);
    return path;
  }

  public String extractRootPath(@NotNull final String path) {
    final int jarSeparatorIndex = path.indexOf(JAR_SEPARATOR);
    assert jarSeparatorIndex >= 0 : "Path passed to JarFileSystem must have jar separator '!/': " + path;
    return path.substring(0, jarSeparatorIndex + JAR_SEPARATOR.length());
  }

  public boolean isCaseSensitive() {
    return true;
  }

  public boolean exists(final VirtualFile fileOrDirectory) {
    return getHandler(fileOrDirectory).exists(fileOrDirectory);
  }

  @NotNull
  public InputStream getInputStream(final VirtualFile file) throws IOException {
    return getHandler(file).getInputStream(file);
  }

  @NotNull
  public byte[] contentsToByteArray(final VirtualFile file) throws IOException {
    return getHandler(file).contentsToByteArray(file);
  }

  public long getLength(final VirtualFile file) {
    return getHandler(file).getLength(file);
  }

  @NotNull
  public OutputStream getOutputStream(final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp)
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
    try {
      if (myNoCopyJarDir!=null && FileUtil.isAncestor(myNoCopyJarDir, originalJar, false)) return false;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    return true;
  }

  public long getTimeStamp(final VirtualFile file) {
    return getHandler(file).getTimeStamp(file);
  }

  public boolean isDirectory(final VirtualFile file) {
    return getHandler(file).isDirectory(file);
  }

  public boolean isWritable(final VirtualFile file) {
    return false;
  }

  public String[] list(final VirtualFile file) {
    return getHandler(file).list(file);
  }

  public void setTimeStamp(final VirtualFile file, final long modstamp) throws IOException {
    getHandler(file).setTimeStamp(file, modstamp);
  }

  public void setWritable(final VirtualFile file, final boolean writableFlag) throws IOException {
    getHandler(file).setWritable(file, writableFlag);
  }

  @NotNull
  public VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vDir.getUrl()));
  }

  public VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vDir.getUrl()));
  }

  public void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
  }

  public void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vFile.getUrl()));
  }

  public VirtualFile copyFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent, @NotNull final String copyName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vFile.getUrl()));
  }

  public void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
    throw new IOException(VfsBundle.message("jar.modification.not.supported.error", vFile.getUrl()));
  }

  public int getRank() {
    return 2;
  }
}
