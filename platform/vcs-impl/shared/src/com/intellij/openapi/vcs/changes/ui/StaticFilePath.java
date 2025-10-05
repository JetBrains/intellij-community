// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ChangesTreeCompatibilityProvider;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
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

  public @NotNull String getPath() {
    return myPath.getPath();
  }

  public @NotNull String getKey() {
    return myKey;
  }

  /**
   * @deprecated Use {@link #resolve()} or {@link com.intellij.vcsUtil.VcsImplUtil#findValidParentAccurately}
   */
  @Deprecated
  public @Nullable VirtualFile getVf() {
    return null;
  }

  public @NotNull FilePath getFilePath() {
    return myPath;
  }

  public @Nullable StaticFilePath getParent() {
    FilePath parentPath = myPath.getParentPath();
    if (parentPath == null) return null;
    String parentKey = myKey.substring(0, parentPath.getPath().length());
    return new StaticFilePath(parentPath, parentKey);
  }

  public @Nullable VirtualFile resolve() {
    return ChangesTreeCompatibilityProvider.getInstance().resolveLocalFile(getPath());
  }
}
