// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

class EmptyLogStorage implements VcsLogStorage {
  public static final @NotNull VcsLogStorage INSTANCE = new EmptyLogStorage();

  private EmptyLogStorage() { }

  @Override
  public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
    return 0;
  }

  @Override
  public @NotNull CommitId getCommitId(int commitIndex) {
    throw new UnsupportedOperationException("Illegal access to empty hash map by index " + commitIndex);
  }

  @Override
  public boolean containsCommit(@NotNull CommitId id) {
    return false;
  }

  @Override
  public void iterateCommits(@NotNull Predicate<? super CommitId> consumer) {
  }

  @Override
  public int getRefIndex(@NotNull VcsRef ref) {
    return 0;
  }

  @Override
  public @Nullable VcsRef getVcsRef(int refIndex) {
    throw new UnsupportedOperationException("Illegal access to empty ref map by index " + refIndex);
  }

  @Override
  public void flush() {
  }
}
