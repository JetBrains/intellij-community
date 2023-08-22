// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.Interner;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.VcsRefType;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public final class VcsRefImpl implements VcsRef {
  private static final Interner<String> ourNames = Interner.createWeakInterner();
  private final @NotNull Hash myCommitHash;
  private final @NotNull String myName;
  private final @NotNull VcsRefType myType;
  private final @NotNull VirtualFile myRoot;

  public VcsRefImpl(@NotNull Hash commitHash, @NotNull String name, @NotNull VcsRefType type, @NotNull VirtualFile root) {
    myCommitHash = commitHash;
    myType = type;
    myRoot = root;
    synchronized (ourNames) {
      myName = ourNames.intern(name);
    }
  }

  @Override
  public @NotNull VcsRefType getType() {
    return myType;
  }

  @Override
  public @NotNull Hash getCommitHash() {
    return myCommitHash;
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public @NotNull VirtualFile getRoot() {
    return myRoot;
  }

  @Override
  public String toString() {
    return String.format("%s:%s(%s|%s)", myRoot.getName(), myName, myCommitHash, myType);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VcsRefImpl ref = (VcsRefImpl)o;

    if (!myCommitHash.equals(ref.myCommitHash)) return false;
    if (!myName.equals(ref.myName)) return false;
    if (!myRoot.equals(ref.myRoot)) return false;
    if (myType != ref.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myCommitHash.hashCode();
    result = 31 * result + (myName.hashCode());
    result = 31 * result + (myRoot.hashCode());
    result = 31 * result + (myType.hashCode());
    return result;
  }
}
