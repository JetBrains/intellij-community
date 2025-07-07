// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.BiDirectionalEnumerator;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

final class InMemoryStorage implements VcsLogStorage {
  private final BiDirectionalEnumerator<CommitId> myCommitIdEnumerator = new BiDirectionalEnumerator<>(1);
  private final BiDirectionalEnumerator<VcsRef> myRefsEnumerator = new BiDirectionalEnumerator<>(1);

  @Override
  public int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root) {
    return getOrPut(hash, root);
  }

  private int getOrPut(@NotNull Hash hash, @NotNull VirtualFile root) {
    return myCommitIdEnumerator.enumerate(new CommitId(hash, root));
  }

  @Override
  public @NotNull CommitId getCommitId(int commitIndex) {
    return myCommitIdEnumerator.getValue(commitIndex);
  }

  @Override
  public boolean containsCommit(@NotNull CommitId id) {
    return myCommitIdEnumerator.contains(id);
  }

  @Override
  public void iterateCommits(@NotNull Predicate<? super CommitId> consumer) {
    myCommitIdEnumerator.forEachValue(consumer);
  }

  @Override
  public int getRefIndex(@NotNull VcsRef ref) {
    return myRefsEnumerator.enumerate(ref);
  }

  @Override
  public @NotNull VcsRef getVcsRef(int refIndex) {
    return myRefsEnumerator.getValue(refIndex);
  }

  @Override
  public void flush() {
    // nothing to flush
  }
}
