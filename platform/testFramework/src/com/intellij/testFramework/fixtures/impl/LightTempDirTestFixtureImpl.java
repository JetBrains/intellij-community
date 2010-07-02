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
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public class LightTempDirTestFixtureImpl extends BaseFixture implements TempDirTestFixture {
  @NotNull private final VirtualFile mySourceRoot;
  private final boolean myUsePlatformSourceRoot;

  public LightTempDirTestFixtureImpl() {
    final VirtualFile fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    mySourceRoot = new WriteAction<VirtualFile>() {
      protected void run(final Result<VirtualFile> result) throws Throwable {
        result.setResult(fsRoot.createChildDirectory(this, "root"));
      }
    }.execute().getResultObject();
    myUsePlatformSourceRoot = false;
  }

  public LightTempDirTestFixtureImpl(boolean usePlatformSourceRoot) {
    myUsePlatformSourceRoot = usePlatformSourceRoot;
    mySourceRoot = null;
  }

  public VirtualFile copyFile(final VirtualFile file, final String targetPath) {
    final String path = PathUtil.getParentPath(targetPath);
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        try {
          VirtualFile targetDir = findOrCreateDir(path);
          final String newName = PathUtil.getFileName(targetPath);
          final VirtualFile existing = targetDir.findChild(newName);
          if (existing != null) {
            existing.setBinaryContent(file.contentsToByteArray());
            return existing;
          }

          return VfsUtil.copyFile(this, file, targetDir, newName);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @NotNull
  public VirtualFile findOrCreateDir(final String path) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        VirtualFile root = getSourceRoot();
        if (path.length() == 0) return root;
        String trimPath = StringUtil.trimStart(path, "/");
        final List<String> dirs = StringUtil.split(trimPath, "/");
        for (String dirName : dirs) {
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
    });
  }

  public VirtualFile copyAll(String dataDir, String targetDir) {
    return copyAll(dataDir, targetDir, VirtualFileFilter.ALL);
  }

  public VirtualFile copyAll(final String dataDir, final String targetDir, @NotNull final VirtualFileFilter filter) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        final VirtualFile from = LocalFileSystem.getInstance().refreshAndFindFileByPath(dataDir);
        assert from != null: "Cannot find testdata directory " + dataDir;
        try {
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
    String thisLevel = relativePath;
    String nextLevel = null;
    final int pos = relativePath.indexOf('/');
    if (pos > 0) {
      thisLevel = relativePath.substring(0, pos);
      nextLevel = relativePath.substring(pos+1);
    }
    VirtualFile child = root.findChild(thisLevel);
    if (child == null) {
      child = root.createChildDirectory(this, thisLevel);
    }
    if (nextLevel != null && nextLevel.length() > 0) {
      return findOrCreateChildDir(child, nextLevel);
    }
    return child;
  }

  public String getTempDirPath() {
    return "temp:///root";
  }

  public VirtualFile getFile(@NonNls String path) {
    final VirtualFile sourceRoot = getSourceRoot();
    final VirtualFile result = sourceRoot.findFileByRelativePath(path);
    if (result == null) {
      sourceRoot.refresh(false, true);
      return sourceRoot.findFileByRelativePath(path);
    }
    return result;
  }

  @NotNull
  public VirtualFile createFile(String targetPath) {
    final String path = PathUtil.getParentPath(targetPath);
    final String name = PathUtil.getFileName(targetPath);
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        try {
          VirtualFile targetDir = findOrCreateDir(path);
          return targetDir.createChildData(this, name);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @NotNull
  public VirtualFile createFile(String targetPath, final String text) throws IOException {
    final VirtualFile file = createFile(targetPath);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        try {
          VfsUtil.saveText(file, text);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
    return file;
  }

  public void deleteAll() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
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
          catch (IOException e) {
            // ignore
          }
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
