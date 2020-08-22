// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.vcs;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.peer.impl.VcsContextFactoryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MockVcsContextFactory extends VcsContextFactoryImpl {
  @NotNull
  private final VirtualFileSystem myFileSystem;

  public MockVcsContextFactory(@NotNull VirtualFileSystem fileSystem) {
    myFileSystem = fileSystem;
  }

  @NotNull
  @Override
  public FilePath createFilePath(@NotNull String path, boolean isDirectory) {
    return new CustomFileSystemFilePath(path, isDirectory);
  }

  private class CustomFileSystemFilePath extends LocalFilePath {
    CustomFileSystemFilePath(@NotNull String path, boolean directory) {
      super(path, directory);
    }

    @Nullable
    @Override
    public VirtualFile getVirtualFile() {
      return myFileSystem.findFileByPath(getPath());
    }
  }
}