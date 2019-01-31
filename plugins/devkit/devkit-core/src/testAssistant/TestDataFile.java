// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface TestDataFile {

  boolean exists();

  @Nullable
  VirtualFile getVirtualFile();

  @NotNull
  String getPath();

  @NotNull
  String getName();

  class Existing implements TestDataFile {
    private final VirtualFile myFile;

    public Existing(VirtualFile file) {myFile = file;}

    @Override
    public boolean exists() {
      return true;
    }

    @Override
    public VirtualFile getVirtualFile() {
      return myFile;
    }

    @NotNull
    @Override
    public String getPath() {
      return myFile.getPath();
    }

    @NotNull
    @Override
    public String getName() {
      return myFile.getName();
    }
  }

  class NonExisting implements TestDataFile {
    private final String myPath;

    public NonExisting(String path) {myPath = path;}

    @Override
    public boolean exists() {
      return false;
    }

    @Override
    public VirtualFile getVirtualFile() {
      return null;
    }

    @NotNull
    @Override
    public String getPath() {
      return myPath;
    }

    @NotNull
    @Override
    public String getName() {
      return PathUtil.getFileName(getPath());
    }
  }

  class LazyResolved implements TestDataFile {
    private final String myPath;
    private VirtualFile myFile;
    private boolean myResolved;

    public LazyResolved(String path) {myPath = path;}

    private void resolve() {
      if (!myResolved) {
        myResolved = true;
        myFile = ReadAction.compute(() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(myPath));
      }
    }

    @Override
    public boolean exists() {
      resolve();
      return getVirtualFile() != null;
    }

    @Override
    public VirtualFile getVirtualFile() {
      resolve();
      return myFile;
    }

    @NotNull
    @Override
    public String getName() {
      return PathUtil.getFileName(getPath());
    }

    @NotNull
    @Override
    public String getPath() {
      return myPath;
    }
  }
}
