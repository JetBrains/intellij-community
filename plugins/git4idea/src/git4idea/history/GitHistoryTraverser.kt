// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsUser
import git4idea.GitCommit

/**
 * Commit index which is used by [GitHistoryTraverser].
 * Can be obtained by [GitHistoryTraverser.traverse] and [GitHistoryTraverser.IndexedRoot.filterCommits] methods.
 *
 * @see subscribeForGitHistoryTraverserCreation
 * @see getTraverser
 */
typealias TraverseCommitId = Int

interface GitHistoryTraverser : Disposable {
  /**
   * Start traversing down through the repository history started from [start].
   * Each commit will be handled by [commitHandler] which could return false to finish traversing
   * (in case of [GitHistoryTraverser.TraverseType.BFS] the remaining elements in the queue will be fully processed).
   *
   * @throws IllegalArgumentException if given [StartNode] doesn't exist in the repository.
   */
  fun traverse(
    root: VirtualFile,
    start: StartNode = StartNode.Head,
    type: TraverseType = TraverseType.DFS,
    commitHandler: Traverse.(id: TraverseCommitInfo) -> Boolean
  )

  /**
   * Subscribe for roots index finishing.
   *
   * [IndexingListener.indexedRootsUpdated] may be called few times with subsets of [roots]
   */
  fun addIndexingListener(roots: Collection<VirtualFile>, disposable: Disposable, listener: IndexingListener)

  /**
   * Load commit hash.
   */
  fun toHash(id: TraverseCommitId): Hash

  /**
   * Load basic commit details like message, hash, commit time, author time, etc.
   * This method can be slow due to Git command execution, so it is better to use this method with commit batches.
   *
   * Result commits order may be different from [ids] if [ids] contains commits from different roots.
   */
  fun loadMetadata(ids: List<TraverseCommitId>): List<VcsCommitMetadata>

  /**
   * Load commits details with its changes.
   * If commit contains huge amount of changes, this method can be slow, so use it only if you need changes.
   *
   * Also, this method can be slow due to Git command execution, so it is better to use this method with commit batches.
   *
   * [fullDetailsHandler] calling order may be different from [ids] if [ids] contains commits from different roots.
   */
  fun loadFullDetails(
    ids: List<TraverseCommitId>,
    requirements: GitCommitRequirements = GitCommitRequirements.DEFAULT,
    fullDetailsHandler: (GitCommit) -> Unit
  )

  fun getCurrentUser(root: VirtualFile): VcsUser?

  /**
   * Allows to request commit details loading. They will be loaded synchronously after [traverse] execution.
   */
  interface Traverse {
    fun loadMetadataLater(id: TraverseCommitId, onLoad: (VcsCommitMetadata) -> Unit)

    fun loadFullDetailsLater(
      id: TraverseCommitId,
      requirements: GitCommitRequirements = GitCommitRequirements.DEFAULT,
      onLoad: (GitCommit) -> Unit
    )
  }

  /**
   * Allows to filter commits and load commit details in the fast way.
   */
  interface IndexedRoot {
    val root: VirtualFile

    /**
     * Return all commits from repository fit by [TraverseCommitsFilter].
     *
     * Note that they may become unreachable from any branches, so it's better to check that given commits are reachable during [traverse].
     */
    fun filterCommits(filter: TraverseCommitsFilter): Collection<TraverseCommitId>

    /**
     * Load commit hash with timestamp and parents.
     *
     * This method uses prepared index and don't execute Git commands.
     */
    fun loadTimedCommit(id: TraverseCommitId): TimedVcsCommit

    /**
     * Load basic commit details like message, hash, commit time, author time, etc.
     *
     * This method uses prepared index and don't execute Git commands.
     */
    fun loadMetadata(id: TraverseCommitId): VcsCommitMetadata

    sealed class TraverseCommitsFilter {
      /**
       * Note that author filter may return commits from another roots, so check that given commits are from [root] during [traverse].
       */
      class Author(val author: VcsUser) : TraverseCommitsFilter()

      /**
       * Commits filter result by [file] has some specific aspects:
       *   * contains only exact renames (file name change without content change);
       *   * contains all trivial merge commits whose subtrees contain [file] changes.
       */
      class File(val file: FilePath) : TraverseCommitsFilter()
    }
  }

  fun interface IndexingListener {
    /**
     * Method shouldn't execute long running tasks.
     */
    fun indexedRootsUpdated(roots: Collection<IndexedRoot>)
  }

  sealed class StartNode {
    class CommitHash(val hash: Hash) : StartNode()
    class Branch(val branchName: String) : StartNode()
    object Head : StartNode()
  }

  enum class TraverseType {
    DFS, BFS
  }

  data class TraverseCommitInfo(
    val id: TraverseCommitId,
    val parents: List<TraverseCommitId>
  )
}