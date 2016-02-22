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
package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;

/**
 * @author Dmitry Avdeev
 */
public class TempDirTestFixtureImpl extends BaseFixture implements TempDirTestFixture {
  private File myTempDir;

  @NotNull
  @Override
  public VirtualFile copyAll(@NotNull String dataDir, @NotNull String targetDir) {
    return copyAll(dataDir, targetDir, VirtualFileFilter.ALL);
  }

  @NotNull
  @Override
  public VirtualFile copyAll(@NotNull final String dataDir, @NotNull final String targetDir, @NotNull final VirtualFileFilter filter) {
    createTempDirectory();
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        try {
          VirtualFile tempDir =
            LocalFileSystem.getInstance().refreshAndFindFileByPath(myTempDir.getCanonicalPath().replace(File.separatorChar, '/'));
          Assert.assertNotNull(tempDir);
          if (targetDir.length() > 0) {
            Assert.assertFalse("nested directories not implemented", targetDir.contains("/"));
            VirtualFile child = tempDir.findChild(targetDir);
            if (child == null) {
              child = tempDir.createChildDirectory(this, targetDir);
            }
            tempDir = child;
          }
          final VirtualFile from = LocalFileSystem.getInstance().refreshAndFindFileByPath(dataDir);
          Assert.assertNotNull(dataDir + " not found", from);
          VfsUtil.copyDirectory(null, from, tempDir, filter);
          return tempDir;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @NotNull
  @Override
  public String getTempDirPath() {
    return createTempDirectory().getAbsolutePath();
  }

  public File createTempFile(String fileName) throws IOException {
    String prefix = StringUtil.getPackageName(fileName);
    if (prefix.length() < 3) {
      prefix += "___";
    }
    String suffix = "." + StringUtil.getShortName(fileName);
    return FileUtil.createTempFile(new File(getTempDirPath()), prefix, suffix, true);
  }

  @Override
  public VirtualFile getFile(@NotNull final String path) {
    return new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws IOException {
        final String fullPath = myTempDir.getCanonicalPath() + '/' + path;
        final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath);
        result.setResult(file);
      }
    }.execute().getResultObject();
  }

  @Override
  @NotNull
  public VirtualFile createFile(@NotNull final String name) {
    final File file = new File(createTempDirectory(), name);
    return ApplicationManager.getApplication().runWriteAction((Computable<VirtualFile>)() -> {
      FileUtil.createIfDoesntExist(file);
      return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    });
  }

  @Override
  @NotNull
  public VirtualFile findOrCreateDir(@NotNull String name) throws IOException {
    return VfsUtil.createDirectories(new File(createTempDirectory(), name).getPath());
  }

  @Override
  @NotNull
  public VirtualFile createFile(@NotNull String name, @NotNull final String text) throws IOException {
    final VirtualFile file = createFile(name);
    new WriteAction() {
      @Override
      protected void run(@NotNull Result result) throws IOException {
        VfsUtil.saveText(file, text);
      }
    }.execute();
    return file;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    createTempDirectory();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      if (myTempDir != null) {
        new WriteAction() {
          @Override
          protected void run(@NotNull Result result) throws IOException {
            findOrCreateDir("").delete(this);
          }
        }.execute();
      }
    }
    finally {
      //noinspection ThrowFromFinallyBlock
      super.tearDown();
    }
  }

  protected File getTempHome() {
    return null;
  }

  @NotNull
  protected File createTempDirectory() {
    try {
      if (myTempDir == null) {
        File tempHome = getTempHome();
        myTempDir = tempHome == null ? FileUtil.createTempDirectory("unitTest", null, false) :
                    FileUtil.createTempDirectory(tempHome, "unitTest", null, false);
      }
      return myTempDir;
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot create temp dir", e);
    }
  }
}