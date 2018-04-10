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
@SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors", "JUnitTestClassNamingConvention", "JUnitTestCaseWithNoTests"})
public class LightTempDirTestFixtureImpl extends BaseFixture implements TempDirTestFixture {
  private final VirtualFile mySourceRoot;
  private final boolean myUsePlatformSourceRoot;

  public LightTempDirTestFixtureImpl() {
    final VirtualFile fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    Assert.assertNotNull(fsRoot);
    try {
      mySourceRoot = WriteAction.computeAndWait(() -> fsRoot.createChildDirectory(this, "root"));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    myUsePlatformSourceRoot = false;
  }

  public LightTempDirTestFixtureImpl(boolean usePlatformSourceRoot) {
    myUsePlatformSourceRoot = usePlatformSourceRoot;
    mySourceRoot = null;
  }

  @Override
  public void tearDown() throws Exception {
    try {
      deleteAll();
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  @NotNull
  public VirtualFile findOrCreateDir(@NotNull final String path) {
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
  public VirtualFile copyAll(@NotNull final String dataDir, @NotNull final String targetDir, @NotNull final VirtualFileFilter filter) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        final VirtualFile from = LocalFileSystem.getInstance().refreshAndFindFileByPath(dataDir);
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
    String trimPath = StringUtil.trimStart(relativePath, "/");
    final List<String> dirs = StringUtil.split(trimPath, "/");
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
        try {
          root = root.createChildDirectory(this, dirName);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
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

  @Override
  @NotNull
  public VirtualFile createFile(@NotNull String targetPath) {
    final String path = PathUtil.getParentPath(targetPath);
    final String name = PathUtil.getFileName(targetPath);
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

  @Override
  @NotNull
  public VirtualFile createFile(@NotNull String name, @NotNull final String text) throws IOException {
    final VirtualFile file = createFile(name);
    WriteAction.runAndWait(() -> VfsUtil.saveText(file, text));
    return file;
  }

  public void deleteAll() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final VirtualFile[] toDelete;
        if (myUsePlatformSourceRoot) {
          toDelete = getSourceRoot().getChildren();
        }
        else {
          toDelete = new VirtualFile[] {mySourceRoot};
        }

        for (VirtualFile file : toDelete) {
          try {
            file.delete(this);
          }
          catch (IOException ignored) { }
        }
      }
    });
  }

  @NotNull
  private VirtualFile getSourceRoot() {
    if (myUsePlatformSourceRoot) {
      return LightPlatformTestCase.getSourceRoot();
    }
    return mySourceRoot;
  }
}