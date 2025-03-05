// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface TestDataFile {

  boolean exists();

  @Nullable
  VirtualFile getVirtualFile();

  @NotNull
  @NlsSafe
  String getPath();

  @NotNull
  @NlsSafe
  String getName();

  class Existing implements TestDataFile {
    private final @NotNull VirtualFile myFile;

    public Existing(@NotNull VirtualFile file) {myFile = file;}

    @Override
    public boolean exists() {
      return true;
    }

    @Override
    public VirtualFile getVirtualFile() {
      return myFile;
    }

    @Override
    public @NotNull String getPath() {
      return myFile.getPath();
    }

    @Override
    public @NotNull String getName() {
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

    @Override
    public @NotNull String getPath() {
      return myPath;
    }

    @Override
    public @NotNull String getName() {
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

    @Override
    public @NotNull String getName() {
      return PathUtil.getFileName(getPath());
    }

    @Override
    public @NotNull String getPath() {
      return myPath;
    }
  }
}
