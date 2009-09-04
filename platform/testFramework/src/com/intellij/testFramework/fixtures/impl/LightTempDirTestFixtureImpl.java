package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
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
    mySourceRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
    myUsePlatformSourceRoot = false;
  }

  public LightTempDirTestFixtureImpl(boolean usePlatformSourceRoot) {
    myUsePlatformSourceRoot = usePlatformSourceRoot;
    mySourceRoot = null;
  }

  public VirtualFile copyFile(final VirtualFile file, String targetPath) {
    int pos = targetPath.lastIndexOf('/');
    final String path = pos < 0 ? "" : targetPath.substring(0, pos);
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        try {
          VirtualFile targetDir = findOrCreateDir(path);
          return VfsUtil.copyFile(this, file, targetDir);
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

  public VirtualFile copyAll(final String dataDir, final String targetDir) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      public VirtualFile compute() {
        final VirtualFile from = LocalFileSystem.getInstance().refreshAndFindFileByPath(dataDir);
        assert from != null: "Cannot find testdata directory " + dataDir;
        try {
          VirtualFile tempDir = getSourceRoot();
          if (targetDir.length() > 0) {
            tempDir = findOrCreateChildDir(tempDir, targetDir);
          }

          VfsUtil.copyDirectory(this, from, tempDir, null);
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
    return "temp:///";
  }

  public VirtualFile getFile(@NonNls String path) {
    return getSourceRoot().findFileByRelativePath(path);
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
        final VirtualFile[] children = getSourceRoot().getChildren();
        for (VirtualFile child : children) {
          try {
            child.delete(this);
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
