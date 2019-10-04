// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public class LightTempDirTestFixtureImpl extends BaseFixture implements TempDirTestFixture {
  private final VirtualFile mySourceRoot;

  public LightTempDirTestFixtureImpl() {
    this(false);
  }

  public LightTempDirTestFixtureImpl(boolean usePlatformSourceRoot) {
    if (usePlatformSourceRoot) {
      mySourceRoot = null;
    }
    else {
      VirtualFile fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
      Assert.assertNotNull(fsRoot);
      try {
        mySourceRoot = WriteAction.computeAndWait(() -> fsRoot.createChildDirectory(this, "root"));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public void tearDown() throws Exception {
    try {
      deleteAll();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  @Override
  public VirtualFile findOrCreateDir(@NotNull String path) {
    return WriteAction.computeAndWait(() -> {
      try {
        return findOrCreateChildDir(getSourceRoot(), path);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @NotNull
  @Override
  public VirtualFile copyAll(@NotNull String dataDir, @NotNull String targetDir) {
    return copyAll(dataDir, targetDir, VirtualFileFilter.ALL);
  }

  @NotNull
  @Override
  public VirtualFile copyAll(@NotNull String dataDir, @NotNull String targetDir, @NotNull VirtualFileFilter filter) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        VirtualFile from = LocalFileSystem.getInstance().refreshAndFindFileByPath(dataDir);
        Assert.assertNotNull("Cannot find testdata directory " + dataDir, from);
        try {
          UsefulTestCase.refreshRecursively(from);

          VirtualFile tempDir = getSourceRoot();
          if (targetDir.length() > 0) {
            tempDir = findOrCreateChildDir(tempDir, targetDir);
          }

          VfsUtil.copyDirectory(this, from, tempDir, filter);
          return tempDir;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  private VirtualFile findOrCreateChildDir(VirtualFile root, String relativePath) throws IOException {
    if (relativePath.length() == 0) return root;

    List<String> dirs = StringUtil.split(StringUtil.trimStart(relativePath, "/"), "/");
    for (String dirName : dirs) {
      if (dirName.equals(".")) continue;

      if (dirName.equals("..")) {
        root = root.getParent();
        if (root == null) throw new IllegalArgumentException("Invalid path: " + relativePath);
        continue;
      }

      VirtualFile dir = root.findChild(dirName);
      if (dir != null) {
        root = dir;
      }
      else {
        root = root.createChildDirectory(this, dirName);
      }
    }

    return root;
  }

  @NotNull
  @Override
  public String getTempDirPath() {
    return "temp:///root";
  }

  @Override
  public VirtualFile getFile(@NotNull String path) {
    VirtualFile sourceRoot = getSourceRoot();
    VirtualFile result = sourceRoot.findFileByRelativePath(path);
    if (result == null) {
      sourceRoot.refresh(false, true);
      result = sourceRoot.findFileByRelativePath(path);
    }
    return result;
  }

  @NotNull
  @Override
  public VirtualFile createFile(@NotNull String targetPath) {
    String path = PathUtil.getParentPath(targetPath);
    String name = PathUtil.getFileName(targetPath);
    try {
      return WriteAction.computeAndWait(() -> {
        VirtualFile targetDir = findOrCreateDir(path);
        return targetDir.createChildData(this, name);
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  @Override
  public VirtualFile createFile(@NotNull String name, @NotNull String text) throws IOException {
    VirtualFile file = createFile(name);
    WriteAction.runAndWait(() -> VfsUtil.saveText(file, text));
    return file;
  }

  public void deleteAll() {
    WriteAction.runAndWait(() -> {
      VirtualFile[] toDelete = mySourceRoot != null ? new VirtualFile[]{mySourceRoot} : getSourceRoot().getChildren();
      for (VirtualFile file : toDelete) {
        try {
          file.delete(this);
        }
        catch (IOException ignored) { }
      }
    });
  }

  @NotNull
  private VirtualFile getSourceRoot() {
    return mySourceRoot != null ? mySourceRoot : LightPlatformTestCase.getSourceRoot();
  }
}