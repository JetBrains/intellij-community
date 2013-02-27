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

/*
 * @author max
 */
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class JarHandler extends JarHandlerBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.jar.JarHandler");

  @NonNls private static final String JARS_FOLDER = "jars";

  private final JarFileSystemImpl myFileSystem;

  public JarHandler(@NotNull JarFileSystemImpl fileSystem, @NotNull String path) {
    super(path);
    myFileSystem = fileSystem;
  }

  public void refreshLocalFileForJar() {
    NewVirtualFile localJarFile = (NewVirtualFile)LocalFileSystem.getInstance().refreshAndFindFileByPath(myBasePath);
    if (localJarFile != null) {
      localJarFile.markDirty();
    }
  }

  @Nullable
  public VirtualFile markDirty() {
    clear();

    final VirtualFile root = JarFileSystem.getInstance().findFileByPath(myBasePath + JarFileSystem.JAR_SEPARATOR);
    if (root instanceof NewVirtualFile) {
      ((NewVirtualFile)root).markDirty();
    }
    return root;
  }

  @Override
  public File getMirrorFile(@NotNull File originalFile) {
    if (!myFileSystem.isMakeCopyOfJar(originalFile)) return originalFile;

    final FileAttributes originalAttributes = FileSystemUtil.getAttributes(originalFile);
    if (originalAttributes == null) return originalFile;

    final String folderPath = getJarsDir();
    if (!new File(folderPath).exists() && !new File(folderPath).mkdirs()) {
      return originalFile;
    }

    final String mirrorName = originalFile.getName() + "." + Integer.toHexString(originalFile.getPath().hashCode());
    final File mirrorFile = new File(folderPath, mirrorName);
    final FileAttributes mirrorAttributes = FileSystemUtil.getAttributes(mirrorFile);

    if (mirrorAttributes == null ||
        originalAttributes.length != mirrorAttributes.length ||
        Math.abs(originalAttributes.lastModified - mirrorAttributes.lastModified) > 2000) {
      return copyToMirror(originalFile, mirrorFile);
    }

    return mirrorFile;
  }

  @NotNull
  private static String getJarsDir() {
    String dir = System.getProperty("jars_dir");
    return dir == null ? PathManager.getSystemPath() + File.separatorChar + JARS_FOLDER : dir;
  }

  @NotNull
  private File copyToMirror(@NotNull File original, @NotNull File mirror) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    if (progress != null) {
      progress.pushState();
      progress.setText(VfsBundle.message("jar.copy.progress", original.getPath()));
      progress.setFraction(0);
    }

    try {
      FileUtil.copy(original, mirror);
    }
    catch (final IOException e) {
      LOG.warn(e);
      final String path = original.getPath();
      final String message = VfsBundle.message("jar.copy.error.message", path, mirror.getPath(), e.getMessage());
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(message, VfsBundle.message("jar.copy.error.title"));
        }
      }, ModalityState.NON_MODAL);

      myFileSystem.setNoCopyJarForPath(path);
      return original;
    }

    if (progress != null) {
      progress.popState();
    }

    return mirror;
  }
}
