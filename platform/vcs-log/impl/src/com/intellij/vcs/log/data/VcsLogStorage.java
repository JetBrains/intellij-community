// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsRef;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Storage for various Log objects like CommitId or VcsRef
 * which quantity is too big to keep them in memory.
 * VcsLogStorage keeps a mapping from integers to those objects
 * allowing to operate with integers, not the objects themselves.
 */
public interface VcsLogStorage {

  /**
   * Returns an integer index that is a unique identifier for a commit with specified hash and root.
   *
   * @param hash commit hash
   * @param root root of the repository for the commit
   * @return a commit index
   */
  int getCommitIndex(@NotNull Hash hash, @NotNull VirtualFile root);

  /**
   * Returns a commit for a specified index or null if this index does not correspond to any commit.
   *
   * @param commitIndex index of a commit
   * @return commit identified by this index or null
   */
  @Nullable
  CommitId getCommitId(int commitIndex);

  /**
   * Return mapping of specified commit indexes to the corresponding commits.
   *
   * @see #getCommitId
   * @return commits identified by the given commit indexes or empty map
   */
  default Map<@NotNull Integer, @NotNull CommitId> getCommitIds(@NotNull Collection<Integer> commitIds) {
    Map<@NotNull Integer, @NotNull CommitId> result = new Int2ObjectOpenHashMap<>();
    for (Integer commitIndex : commitIds) {
      CommitId commitId = getCommitId(commitIndex);
      if (commitId != null) {
        result.put(commitIndex, commitId);
      }
    }

    return result;
  }

  /**
   * Iterates over known commit ids. Stops when processor returns false.
   */
  void iterateCommits(@NotNull Predicate<? super CommitId> consumer);

  /**
   * Checks whether the storage contains the commit.
   *
   * @param id commit to check
   * @return true if storage contains the commit, false otherwise
   */
  boolean containsCommit(@NotNull CommitId id);

  /**
   * Iterates over known commit ids to find the first one which satisfies given condition.
   *
   * @return matching commit or null if no commit matches the given condition
   */
  default @Nullable CommitId findCommitId(@NotNull Predicate<? super CommitId> condition) {
    Ref<CommitId> hashRef = Ref.create();
    iterateCommits(commitId -> {
      boolean matches = condition.test(commitId);
      if (matches) {
        hashRef.set(commitId);
      }
      return !matches;
    });
    return hashRef.get();
  }

  /**
   * Returns an integer index that is a unique identifier for a reference.
   *
   * @param ref reference
   * @return a reference index
   */
  int getRefIndex(@NotNull VcsRef ref);

  /**
   * Returns a reference for a specified index or null if this index does not correspond to any reference.
   *
   * @param refIndex index of a reference
   * @return reference identified by this index or null
   */
  @Nullable
  VcsRef getVcsRef(int refIndex);

  /**
   * Forces data in the storage to be written on disk.
   */
  void flush();
}
