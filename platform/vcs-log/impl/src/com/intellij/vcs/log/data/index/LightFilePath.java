// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

final class LightFilePath {
  private final @NotNull VirtualFile myRoot;
  private final @NotNull String myRelativePath;

  LightFilePath(@NotNull VirtualFile root, @NotNull String relativePath) {
    myRoot = root;
    myRelativePath = relativePath;
  }

  LightFilePath(@NotNull VirtualFile root, @NotNull FilePath filePath) {
    this(root, VcsFileUtil.relativePath(root, filePath));
  }

  public @NotNull VirtualFile getRoot() {
    return myRoot;
  }

  public @NotNull String getRelativePath() {
    return myRelativePath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LightFilePath path = (LightFilePath)o;
    return myRoot.getPath().equals(path.myRoot.getPath()) &&
           myRelativePath.equals(path.myRelativePath);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myRoot.getPath(), myRelativePath);
  }
}
