// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StaticFilePath {
  private final String myKey; // canonical path form for case-insensitive systems
  private final FilePath myPath;

  public StaticFilePath(@NotNull FilePath path) {
    this(path, FilePathsHelper.convertPath(path));
  }

  private StaticFilePath(@NotNull FilePath path, @NotNull String key) {
    myPath = path;
    myKey = key;
  }

  public boolean isDirectory() {
    return myPath.isDirectory();
  }

  @NotNull
  public String getPath() {
    return myPath.getPath();
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
    return myPath;
  }

  @Nullable
  public StaticFilePath getParent() {
    FilePath parentPath = myPath.getParentPath();
    if (parentPath == null) return null;
    String parentKey = myKey.substring(0, parentPath.getPath().length());
    return new StaticFilePath(parentPath, parentKey);
  }

  @Nullable
  public VirtualFile resolve() {
    return LocalFileSystem.getInstance().findFileByPath(getPath());
  }
}
