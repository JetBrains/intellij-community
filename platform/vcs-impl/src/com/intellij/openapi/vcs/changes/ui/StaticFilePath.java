// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class StaticFilePath {
  private final String myKey;
  private final String myPath;
  private final boolean myIsDirectory;
  private final VirtualFile myVf;

  public StaticFilePath(boolean isDirectory, @NotNull String path, @Nullable VirtualFile vf) {
    this(isDirectory, path, FilePathsHelper.convertPath(path), vf);
  }

  private StaticFilePath(boolean isDirectory, @NotNull String path, @NotNull String key, @Nullable VirtualFile vf) {
    myIsDirectory = isDirectory;
    myPath = path;
    myKey = key;
    myVf = vf;
  }

  public boolean isDirectory() {
    return myIsDirectory;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @NotNull
  public String getKey() {
    return myKey;
  }

  @Nullable
  public VirtualFile getVf() {
    return myVf;
  }

  @Nullable
  public StaticFilePath getParent() {
    final int idx = myKey.lastIndexOf('/');
    if (idx == -1 || idx == 0) return null;
    return new StaticFilePath(true, myPath.substring(0, idx), myKey.substring(0, idx), myVf == null ? null : myVf.getParent());
  }

  @Nullable
  public VirtualFile resolve() {
    VirtualFile result = getVf();
    if (result == null) {
      result = LocalFileSystem.getInstance().findFileByIoFile(new File(getPath()));
    }
    return result;
  }
}
