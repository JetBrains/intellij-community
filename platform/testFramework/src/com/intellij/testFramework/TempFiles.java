// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class TempFiles {
  private final Collection<File> myFilesToDelete;

  public TempFiles(@NotNull Collection<File> filesToDelete) {
    myFilesToDelete = filesToDelete;
  }

  @Nullable
  public VirtualFile createVFile(@NotNull String prefix) {
    return getVFileByFile(createTempFile(prefix));
  }

  @Nullable
  public VirtualFile createVFile(@NotNull String prefix, String postfix) {
    return getVFileByFile(createTempFile(prefix, postfix));
  }

  @NotNull
  public File createTempFile(@NotNull String prefix) {
    return createTempFile(prefix, null);
  }

  @NotNull
  public File createTempFile(@NotNull String prefix, String suffix) {
    return createTempFile(prefix, suffix, true);
  }

  @NotNull
  public File createTempFile(@NotNull String prefix, String suffix, boolean isRefreshVfs) {
    try {
      File tempFile = FileUtilRt.createTempFile(prefix, suffix, false);
      tempFileCreated(tempFile);
      if (isRefreshVfs) {
        getVFileByFile(tempFile);
      }
      return tempFile;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void tempFileCreated(@NotNull File tempFile) {
    myFilesToDelete.add(tempFile);
  }

  @Nullable
  public static VirtualFile getVFileByFile(@NotNull File tempFile) {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
  }

  @NotNull
  public File createTempDir() {
    return createTempDir("dir");
  }

  @NotNull
  private File createTempDir(@NotNull String prefix) {
    try {
      File dir = FileUtil.createTempDirectory(prefix, "test",false);
      tempFileCreated(dir);
      PlatformTestCase.synchronizeTempDirVfs(getVFileByFile(dir));
      return dir;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public VirtualFile createTempVDir() {
    return createTempVDir("dir");
  }

  @Nullable
  public VirtualFile createTempVDir(@NotNull String prefix) {
    return getVFileByFile(createTempDir(prefix));
  }

  public void deleteAll() {
    for (File file : myFilesToDelete) {
      if (!FileUtil.delete(file)) {
        //noinspection SSBasedInspection
        file.deleteOnExit();
      }
    }
  }

  public VirtualFile createVFile(@NotNull final VirtualFile parentDir, @NotNull final String name, @NotNull final String text) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        try {
          final VirtualFile virtualFile = parentDir.createChildData(this, name);
          VfsUtil.saveText(virtualFile, text + "\n");
          return virtualFile;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }
}
