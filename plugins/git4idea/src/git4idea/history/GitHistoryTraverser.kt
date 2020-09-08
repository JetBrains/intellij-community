// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsUser
import git4idea.GitCommit

/**
 * Commit index which is used by [GitHistoryTraverser]
 * Can be obtained by [GitHistoryTraverser.traverse] and [GitHistoryTraverser.filterCommits] methods
 */
typealias TraverseCommitId = Int

interface GitHistoryTraverser {
  val root: VirtualFile

  /**
   * Start traversing down through the repository history started from [start] [Hash].
   * Each commit will be handled by [commitHandler] which could return false to finish traversing
   * (in case of [GitHistoryTraverser.TraverseType.BFS] the remaining elements in the queue will be fully processed).
   *
   * @see traverseFromHead
   */
  fun traverse(start: Hash, type: TraverseType, commitHandler: Traverse.(id: TraverseCommitId) -> Boolean)

  fun traverseFromHead(type: TraverseType, commitHandler: Traverse.(id: TraverseCommitId) -> Boolean)

  /**
   * Return all commits from repository fit by [TraverseCommitsFilter].
   */
  fun filterCommits(filter: TraverseCommitsFilter): Collection<TraverseCommitId>

  /**
   * Load commit hash.
   */
  fun toHash(id: TraverseCommitId): Hash

  /**
   * Load commit hash with timestamp.
   */
  fun loadTimedCommit(id: TraverseCommitId): TimedVcsCommit

  /**
   * Load basic commit details like message, hash, commit time, author time, etc.
   * This method is slower than [toHash] and [loadTimedCommit], so it is better to use this method with commit batches.
   */
  fun loadMetadata(ids: List<TraverseCommitId>): List<VcsCommitMetadata>

  /**
   * Load commits details with its changes.
   * If commit contains huge amount of changes, this method can be slow, so use it only if you need changes.
   */
  fun loadFullDetails(ids: List<TraverseCommitId>, fullDetailsHandler: (GitCommit) -> Unit)

  /**
   * Allows to request commit details loading. They will be loaded synchronously after [traverse] execution.
   */
  interface Traverse {
    fun loadMetadataLater(id: TraverseCommitId, onLoad: (VcsCommitMetadata) -> Unit)

    fun loadFullDetailsLater(id: TraverseCommitId, onLoad: (GitCommit) -> Unit)
  }

  sealed class TraverseCommitsFilter {
    class Author(val author: VcsUser) : TraverseCommitsFilter()

    class File(val file: FilePath) : TraverseCommitsFilter()
  }

  enum class TraverseType {
    DFS, BFS
  }
}