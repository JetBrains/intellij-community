/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
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
    try {
      File tempFile = FileUtil.createTempFile(prefix, suffix);
      tempFileCreated(tempFile);
      getVFileByFile(tempFile);
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
