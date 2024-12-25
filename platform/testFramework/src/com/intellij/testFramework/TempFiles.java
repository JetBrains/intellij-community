// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public final class TempFiles {
  private final Collection<Path> myFilesToDelete;

  public TempFiles(@NotNull Collection<Path> filesToDelete) {
    myFilesToDelete = filesToDelete;
  }

  public @NotNull VirtualFile createVFile(@NotNull String prefix) {
    return getVFileByFile(createTempFile(prefix));
  }

  public @NotNull VirtualFile createVFile(@NotNull String prefix, String postfix) {
    return getVFileByFile(createTempFile(prefix, postfix));
  }

  public @NotNull File createTempFile(@NotNull String prefix) {
    return createTempFile(prefix, null);
  }

  public @NotNull File createTempFile(@NotNull String prefix, String suffix) {
    return createTempFile(prefix, suffix, true);
  }

  public @NotNull File createTempFile(@NotNull String prefix, String suffix, boolean isRefreshVfs) {
    try {
      File tempFile = FileUtilRt.createTempFile(prefix, suffix, false);
      tempFileCreated(tempFile.toPath());
      if (isRefreshVfs) {
        getVFileByFile(tempFile);
      }
      return tempFile;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void tempFileCreated(@NotNull Path tempFile) {
    myFilesToDelete.add(tempFile);
  }

  public static VirtualFile getVFileByFile(@NotNull File tempFile) {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
  }

  public @NotNull File createTempDir() {
    return createTempDir("dir");
  }

  private @NotNull File createTempDir(@NotNull String prefix) {
    try {
      File dir = FileUtil.createTempDirectory(prefix, "test",false);
      tempFileCreated(dir.toPath());
      HeavyPlatformTestCase.synchronizeTempDirVfs(getVFileByFile(dir));
      return dir;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public @NotNull VirtualFile createTempVDir() {
    return createTempVDir("dir");
  }

  public @NotNull VirtualFile createTempVDir(@NotNull String prefix) {
    return getVFileByFile(createTempDir(prefix));
  }

  public void deleteAll() {
    for (Path file : myFilesToDelete) {
      PathKt.delete(file);
    }
  }

  public @NotNull VirtualFile createVFile(@NotNull VirtualFile parentDir, @NotNull String name, @NotNull String text) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<>() {
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
