// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class CommitId {
  private final @NotNull Hash myHash;
  private final @NotNull VirtualFile myRoot;

  public CommitId(@NotNull Hash hash, @NotNull VirtualFile root) {
    myHash = hash;
    myRoot = root;
  }

  public @NotNull Hash getHash() {
    return myHash;
  }

  public @NotNull VirtualFile getRoot() {
    return myRoot;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CommitId commitId = (CommitId)o;

    if (!myHash.equals(commitId.myHash)) return false;
    if (!myRoot.equals(commitId.myRoot)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myHash.hashCode();
    result = 31 * result + myRoot.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return myHash.asString() + "(" + myRoot + ")";
  }
}
