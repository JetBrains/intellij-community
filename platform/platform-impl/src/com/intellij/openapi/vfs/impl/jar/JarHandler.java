/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileSystemInterface;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public class JarHandler extends JarHandlerBase implements FileSystemInterface {
  @NonNls private static final String JARS_FOLDER = "jars";

  private final JarFileSystemImpl myFileSystem;

  public JarHandler(final JarFileSystemImpl fileSystem, String path) {
    super(path);
    myFileSystem = fileSystem;
  }

  public void refreshLocalFileForJar() {
    NewVirtualFile localJarFile = (NewVirtualFile)LocalFileSystem.getInstance().refreshAndFindFileByPath(myBasePath);
    if (localJarFile != null) {
      localJarFile.markDirty();
    }
  }

  public void dispose() {
  }

  @Nullable
  public VirtualFile markDirty() {
    synchronized (lock) {
      myRelPathsToEntries.clear();
      myZipFile.set(null);

      final NewVirtualFile root = (NewVirtualFile)
        JarFileSystem.getInstance().findFileByPath(myBasePath + JarFileSystem.JAR_SEPARATOR);
      if (root != null) {
        root.markDirty();
        return root;
      }
      return null;
    }
  }

  @Override
  public File getMirrorFile(File originalFile) {
    if (!myFileSystem.isMakeCopyOfJar(originalFile) || !originalFile.exists()) return originalFile;

    String folderPath = getJarsDir();
    if (!new File(folderPath).exists()) {
      if (!new File(folderPath).mkdirs()) {
        return originalFile;
      }
    }

    String fileName = originalFile.getName() + "." + Integer.toHexString(originalFile.getPath().hashCode());
    final File mirror = new File(folderPath, fileName);

    if (!mirror.exists() || Math.abs(originalFile.lastModified() - mirror.lastModified()) > 2000) {
      return copyToMirror(originalFile, mirror);
    }

    return mirror;
  }

  private static String getJarsDir() {
    String dir = System.getProperty("jars_dir");
    return dir == null ? PathManager.getSystemPath() + File.separatorChar + JARS_FOLDER : dir;
  }

  private File copyToMirror(final File original, final File mirror) {
    ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator progress = progressManager.getProgressIndicator();
    if (progress != null){
      progress.pushState();
      progress.setText(VfsBundle.message("jar.copy.progress", original.getPath()));
      progress.setFraction(0);
    }

    try{
      FileUtil.copy(original, mirror);
    }
    catch(final IOException e){
      final String path1 = original.getPath();
      final String path2 = mirror.getPath();
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          Messages.showMessageDialog(VfsBundle.message("jar.copy.error.message", path1, path2, e.getMessage()), VfsBundle.message("jar.copy.error.title"),
                                     Messages.getErrorIcon());
        }
      }, ModalityState.NON_MODAL);

      myFileSystem.setNoCopyJarForPath(path1);
      return original;
    }

    if (progress != null){
      progress.popState();
    }

    return mirror;
  }

  @Override
  public boolean isWritable(@NotNull final VirtualFile file) {
    return false;
  }

  private static void throwReadOnly() throws IOException {
    throw new IOException("Jar file system is read-only");
  }

  @Override
  @NotNull
  @SuppressWarnings({"ConstantConditions"})
  public OutputStream getOutputStream(@NotNull final VirtualFile file, final Object requestor, final long modStamp, final long timeStamp) throws IOException {
    throwReadOnly();
    return null; // Unreachable
  }

  @Override
  @SuppressWarnings({"ConstantConditions"})
  public VirtualFile copyFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent, @NotNull final String copyName) throws IOException {
    throwReadOnly();
    return null;
  }

  @Override
  public void moveFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final VirtualFile newParent) throws IOException {
    throwReadOnly();
  }

  @Override
  public void renameFile(final Object requestor, @NotNull final VirtualFile file, @NotNull final String newName) throws IOException {
    throwReadOnly();
  }

  @Override
  public void setTimeStamp(@NotNull final VirtualFile file, final long timeStamp) throws IOException {
    throwReadOnly();
  }

  @Override
  public void setWritable(@NotNull final VirtualFile file, final boolean writableFlag) throws IOException {
    throwReadOnly();
  }

  @Override
  public boolean isSymLink(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public String resolveSymLink(@NotNull VirtualFile file) {
    return null;
  }

  @Override
  public boolean isSpecialFile(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  @SuppressWarnings({"ConstantConditions"})
  public VirtualFile createChildDirectory(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String dir) throws IOException {
    throwReadOnly();
    return null;
  }

  @Override
  @SuppressWarnings({"ConstantConditions"})
  public VirtualFile createChildFile(final Object requestor, @NotNull final VirtualFile parent, @NotNull final String file) throws IOException {
    throwReadOnly();
    return null;
  }

  @Override
  public void deleteFile(final Object requestor, @NotNull final VirtualFile file) throws IOException {
    throwReadOnly();
  }
}
