// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull private final Hash myCommitHash;
  @NotNull private final String myName;
  @NotNull private final VcsRefType myType;
  @NotNull private final VirtualFile myRoot;

  public VcsRefImpl(@NotNull Hash commitHash, @NotNull String name, @NotNull VcsRefType type, @NotNull VirtualFile root) {
    myCommitHash = commitHash;
    myType = type;
    myRoot = root;
    synchronized (ourNames) {
      myName = ourNames.intern(name);
    }
  }

  @Override
  @NotNull
  public VcsRefType getType() {
    return myType;
  }

  @Override
  @NotNull
  public Hash getCommitHash() {
    return myCommitHash;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public VirtualFile getRoot() {
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
