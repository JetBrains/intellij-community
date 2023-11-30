// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StaticFilePath {
  private final String myKey;
  private final String myPath;
  private final boolean myIsDirectory;

  public StaticFilePath(boolean isDirectory, @NotNull String path) {
    this(isDirectory, path, FilePathsHelper.convertPath(path));
  }

  private StaticFilePath(boolean isDirectory, @NotNull String path, @NotNull String key) {
    myIsDirectory = isDirectory;
    myPath = path;
    myKey = key;
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

  /**
   * @deprecated Use {@link #resolve()} or {@link com.intellij.vcsUtil.VcsImplUtil#findValidParentAccurately}
   */
  @Nullable
  @Deprecated
  public VirtualFile getVf() {
    return null;
  }

  @NotNull
  public FilePath getFilePath() {
    return VcsUtil.getFilePath(myPath, myIsDirectory);
  }

  @Nullable
  public StaticFilePath getParent() {
    final int idx = myKey.lastIndexOf('/');
    if (idx == -1 || idx == 0) return null;
    return new StaticFilePath(true, myPath.substring(0, idx), myKey.substring(0, idx));
  }

  @Nullable
  public VirtualFile resolve() {
    return LocalFileSystem.getInstance().findFileByPath(getPath());
  }
}
