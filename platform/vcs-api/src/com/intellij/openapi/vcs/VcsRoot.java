// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class VcsRoot {
  private final @Nullable AbstractVcs myVcs;
  private final @NotNull VirtualFile myPath;

  private int hashcode;

  public VcsRoot(@Nullable AbstractVcs vcs, @NotNull VirtualFile path) {
    myVcs = vcs;
    myPath = path;
  }

  public @Nullable AbstractVcs getVcs() {
    return myVcs;
  }

  public @NotNull VirtualFile getPath() {
    return myPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsRoot root = (VcsRoot)o;
    return Objects.equals(myPath, root.myPath) && Objects.equals(myVcs, root.myVcs);
  }

  @Override
  public int hashCode() {
    if (hashcode == 0) {
      hashcode = myVcs != null ? myVcs.hashCode() : 0;
      hashcode = 31 * hashcode + myPath.hashCode();
    }
    return hashcode;
  }

  @Override
  public @NonNls String toString() {
    return String.format("VcsRoot{vcs=%s, path=%s}", myVcs, myPath);
  }
}