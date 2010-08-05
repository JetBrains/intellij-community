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

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * @author Dmitry Avdeev
 */
public class TempDirTestFixtureImpl extends BaseFixture implements TempDirTestFixture {
  private final ArrayList<File> myFilesToDelete = new ArrayList<File>();
  private File myTempDir;

  public VirtualFile copyFile(VirtualFile file, String targetPath) {
    try {
      createTempDirectory();
      VirtualFile tempDir =
        LocalFileSystem.getInstance().refreshAndFindFileByPath(myTempDir.getCanonicalPath().replace(File.separatorChar, '/'));
      return VfsUtil.copyFile(this, file, tempDir);
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot copy " + file, e);
    }
  }

  public VirtualFile copyAll(String dataDir, String targetDir) {
    return copyAll(dataDir, targetDir, VirtualFileFilter.ALL);
  }

  public VirtualFile copyAll(final String dataDir, final String targetDir, @NotNull final VirtualFileFilter filter) {
    createTempDirectory();
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        try {
          VirtualFile tempDir =
            LocalFileSystem.getInstance().refreshAndFindFileByPath(myTempDir.getCanonicalPath().replace(File.separatorChar, '/'));
          if (targetDir.length() > 0) {
            assert !targetDir.contains("/") : "nested directories not implemented";
            VirtualFile child = tempDir.findChild(targetDir);
            if (child == null) {
              child = tempDir.createChildDirectory(this, targetDir);
            }
            tempDir = child;
          }
          final VirtualFile from = LocalFileSystem.getInstance().refreshAndFindFileByPath(dataDir);
          assert from != null : dataDir + " not found";
          VfsUtil.copyDirectory(null, from, tempDir, filter);
          return tempDir;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  public String getTempDirPath() {
    return createTempDirectory().getAbsolutePath();
  }

  @Nullable
  public VirtualFile getFile(final String path) {

    final Ref<VirtualFile> result = new Ref<VirtualFile>(null);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          final String fullPath = myTempDir.getCanonicalPath().replace(File.separatorChar, '/') + "/" + path;
          final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath);
          result.set(file);
        }
        catch (IOException e) {
          assert false : "Cannot find " + path + ": " + e;
        }
      }
    });
    return result.get();
  }

  @NotNull
  public VirtualFile createFile(final String name) {
    final File file = createTempDirectory();
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        final File file1 = new File(file, name);
        FileUtil.createIfDoesntExist(file1);
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file1);
      }
    });
  }

  @NotNull
  public VirtualFile findOrCreateDir(String name) throws IOException {
    return VfsUtil.createDirectories(new File(createTempDirectory(), name).getPath());
  }

  @NotNull
  public VirtualFile createFile(final String name, final String text) throws IOException {
    final VirtualFile file = createFile(name);
    VfsUtil.saveText(file, text);
    return file;
  }

  public void setUp() throws Exception {
    super.setUp();
    createTempDirectory();
  }

  public void tearDown() throws Exception {
    for (final File fileToDelete : myFilesToDelete) {
      boolean deleted = FileUtil.delete(fileToDelete);
      assert deleted : "Can't delete "+fileToDelete;
    }
    super.tearDown();
  }

  protected File getTempHome() {
    return null;
  }

  protected File createTempDirectory() {
    try {
      if (myTempDir == null) {
        File th = getTempHome();
        myTempDir = th != null ? FileUtil.createTempDirectory(th, "unitTest", null) : FileUtil.createTempDirectory("unitTest", null);
        myFilesToDelete.add(myTempDir);
      }
      return myTempDir;
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot create temp dir", e);
    }
  }

}
