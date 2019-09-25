// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
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
  public VirtualFile copyAll(@NotNull String dataDir, @NotNull String targetDir, @NotNull VirtualFileFilter filter) {
    createTempDirectory();
    return WriteAction.computeAndWait(() -> {
      try {
        VirtualFile tempDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(myTempDir.getPath());
        Assert.assertNotNull(tempDir);
        if (!targetDir.isEmpty()) {
          Assert.assertFalse("nested directories not implemented", targetDir.contains("/"));
          VirtualFile child = tempDir.findChild(targetDir);
          if (child == null) child = tempDir.createChildDirectory(this, targetDir);
          tempDir = child;
        }
        VirtualFile from = LocalFileSystem.getInstance().refreshAndFindFileByPath(dataDir);
        Assert.assertNotNull(dataDir + " not found", from);
        VfsUtil.copyDirectory(null, from, tempDir, filter);
        return tempDir;
      }
      catch (IOException e) {
        throw new RuntimeException(e);
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
    if (prefix.length() < 3) prefix += "___";
    String suffix = "." + StringUtil.getShortName(fileName);
    File file = FileUtil.createTempFile(new File(getTempDirPath()), prefix, suffix, true);
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), file.getPath());
    return file;
  }

  @Override
  public VirtualFile getFile(@NotNull String path) {
    String fullPath = myTempDir.getPath() + '/' + path;
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), fullPath);
    return WriteAction.computeAndWait(() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath));
  }

  @NotNull
  @Override
  public VirtualFile createFile(@NotNull String name) {
    File file = new File(createTempDirectory(), name);
    FileUtil.createIfDoesntExist(file);
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), file.getPath());
    return WriteAction.computeAndWait(() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file));
  }

  @NotNull
  @Override
  public VirtualFile findOrCreateDir(@NotNull String name) throws IOException {
    File file = new File(createTempDirectory(), name);
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), file.getPath());
    return VfsUtil.createDirectories(file.getPath());
  }

  @NotNull
  @Override
  public VirtualFile createFile(@NotNull String name, @NotNull String text) throws IOException {
    VirtualFile file = createFile(name);
    WriteAction.runAndWait(() -> VfsUtil.saveText(file, text));
    return file;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    createTempDirectory();
  }

  @Override
  public void tearDown() throws Exception {
    if (myTempDir != null && deleteOnTearDown()) {
      try {
        WriteAction.runAndWait(() -> findOrCreateDir("").delete(this));
      }
      catch (Throwable e) {
        addSuppressedException(e);
      }
      finally {
        super.tearDown();
      }
    }
  }

  protected boolean deleteOnTearDown() {
    return true;
  }

  protected File getTempHome() {
    return null;
  }

  @NotNull
  private File createTempDirectory() {
    if (myTempDir == null) {
      myTempDir = doCreateTempDirectory();
    }
    return myTempDir;
  }

  @NotNull
  protected File doCreateTempDirectory() {
    try {
      File tempHome = getTempHome();
      return tempHome != null
             ? FileUtil.createTempDirectory(tempHome, "unitTest", null, false)
             : FileUtil.createTempDirectory("unitTest", null, false);
    }
    catch (IOException e) {
      throw new RuntimeException("Cannot create temp dir", e);
    }
  }
}