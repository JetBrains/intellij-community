// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.vcs.log.data

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsRef
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import java.util.function.Predicate

/**
 * Storage for various Log objects like CommitId or VcsRef which quantity is too big to keep them in memory.
 * VcsLogStorage keeps a mapping from integers to those objects allowing to operate with integers, not the objects themselves.
 */
interface VcsLogStorage {
  /**
   * Returns an integer index that is a unique identifier for a commit with specified hash and root.
   *
   * @param hash commit hash
   * @param root root of the repository for the commit
   * @return a commit index
   */
  fun getCommitIndex(hash: Hash, root: VirtualFile): Int

  /**
   * Returns a commit for a specified index or null if this index does not correspond to any commit.
   *
   * @param commitIndex index of a commit
   * @return commit identified by this index or null
   */
  fun getCommitId(commitIndex: Int): CommitId?

  /**
   * Return mapping of specified commit indexes to the corresponding commits.
   *
   * @see .getCommitId
   *
   * @return commits identified by the given commit indexes or empty map
   */
  fun getCommitIds(commitIds: Collection<Int>): Map<Int, CommitId> {
    val result = Int2ObjectOpenHashMap<CommitId>()
    for (commitIndex in commitIds) {
      getCommitId(commitIndex)?.let {
        result.put(commitIndex, it)
      }
    }
    return result
  }

  /**
   * Iterates over known commit ids. Stops when the processor returns false.
   */
  fun iterateCommits(consumer: (Predicate<in CommitId>))

  /**
   * Checks whether the storage contains the commit.
   *
   * @param id commit to check
   * @return true if storage contains the commit, false otherwise
   */
  fun containsCommit(id: CommitId): Boolean

  /**
   * Iterates over known commit ids to find the first one, which satisfies a given condition.
   *
   * @return matching commit or null if no commit matches the given condition
   */
  fun findCommitId(condition: Predicate<in CommitId>): CommitId? {
    var result: CommitId? = null
    iterateCommits { commitId ->
      val matches = condition.test(commitId)
      if (matches) {
        result = commitId
      }
      !matches
    }
    return result
  }

  /**
   * Returns an integer index that is a unique identifier for a reference.
   *
   * @param ref reference
   * @return a reference index
   */
  fun getRefIndex(ref: VcsRef): Int

  /**
   * Returns a reference for a specified index or null if this index does not correspond to any reference.
   *
   * @param refIndex index of a reference
   * @return reference identified by this index or null
   */
  fun getVcsRef(refIndex: Int): VcsRef?

  /**
   * Forces data in the storage to be written on disk.
   */
  fun flush()
}
