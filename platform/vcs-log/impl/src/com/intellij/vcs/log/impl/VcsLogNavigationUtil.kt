// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntRef
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.data.CommitIdByStringCondition
import com.intellij.vcs.log.data.DataPack.ErrorDataPack
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.roots
import com.intellij.vcs.log.graph.VcsLogVisibleGraphIndex
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.ui.VcsLogNotificationIdsHolder
import com.intellij.vcs.log.ui.VcsLogUiEx
import com.intellij.vcs.log.ui.VcsLogUiEx.JumpResult
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.VisiblePack.ErrorVisiblePack
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.guava.await
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.CompletableFuture

object VcsLogNavigationUtil {
  private val LOG = logger<VcsLogNavigationUtil>()

  @JvmStatic
  fun jumpToRevisionAsync(project: Project, root: VirtualFile, hash: Hash, filePath: FilePath? = null): CompletableFuture<Boolean> =
    VcsProjectLog.getInstance(project).showRevisionAsync(root, hash, filePath).asCompletableFuture()

  @Internal
  fun VcsLogUiEx.showCommitSync(hash: Hash, root: VirtualFile, requestFocus: Boolean): Boolean {
    return when (jumpToCommitSyncInternal(hash, root, true, requestFocus)) {
      JumpResult.SUCCESS -> true
      JumpResult.COMMIT_NOT_FOUND -> {
        LOG.warn("Commit $hash for $root not found in $this")
        false
      }
      JumpResult.COMMIT_DOES_NOT_MATCH -> false
    }
  }

  @Internal
  suspend fun VcsLogUiEx.showCommit(hash: Hash, root: VirtualFile, requestFocus: Boolean): Boolean {
    return when (jumpToCommitInternal(hash, root, true, requestFocus).await()) {
      JumpResult.SUCCESS -> true
      JumpResult.COMMIT_NOT_FOUND -> {
        LOG.warn("Commit $hash for $root not found in $this")
        false
      }
      JumpResult.COMMIT_DOES_NOT_MATCH -> false
    }
  }

  /**
   * Asynchronously selects the commit node at the given [row] in commit graph.
   * @param row      target row
   * @param silently skip showing notification when the target is not found
   * @param focus    focus the table
   */
  @JvmStatic
  fun VcsLogUiEx.jumpToGraphRow(row: VcsLogVisibleGraphIndex, silently: Boolean, focus: Boolean) {
    jumpTo(row, { visiblePack, r ->
      if (visiblePack.visibleGraph.visibleCommitCount <= r) return@jumpTo -1
      r
    }, SettableFuture.create(), silently, focus)
  }

  @Deprecated("Prefer using jumpToBranch(repositoryRoot: VirtualFile?, branchName: String, silently: Boolean, focus: Boolean)")
  @JvmStatic
  fun VcsLogUiEx.jumpToBranch(branchName: String, silently: Boolean, focus: Boolean) {
    jumpToBranch(null, branchName, silently, focus)
  }

  /**
   * Asynchronously selects the commit node at the given branch head.
   * @Param repositoryRoot target repository root, if known
   * @param branchName name of the target branch
   * @param silently   skip showing notification when the target is not found
   * @param focus      focus the table
   */
  @JvmStatic
  fun VcsLogUiEx.jumpToBranch(repositoryRoot: VirtualFile?, branchName: String, silently: Boolean, focus: Boolean) {
    jumpTo(branchName, { visiblePack, branch ->
      return@jumpTo getBranchRow(logData, visiblePack, branch, repositoryRoot)
    }, SettableFuture.create(), silently, focus)
  }

  /**
   * Asynchronously selects the commit node defined by the given reference (commit hash, branch or tag).
   *
   * Note: this function decides if the provided reference is a hash or a branch/tag once at the start.
   * This may not work as expected when log is not up-to-date, since all the branches and tags are not available yet.
   *
   * @Param repositoryRoot target repository root, if known
   * @param reference target reference (commit hash, branch or tag)
   * @param silently  skip showing notification when the target is not found
   * @param focus     focus the table
   * @return future result (success or failure)
   */
  @JvmStatic
  fun VcsLogUiEx.jumpToRefOrHash(repositoryRoot: VirtualFile?, reference: String, silently: Boolean, focus: Boolean): ListenableFuture<Boolean> {
    if (reference.isBlank()) return Futures.immediateFuture(false)
    val future = SettableFuture.create<Boolean>()
    val refs = dataPack.refs
    ApplicationManager.getApplication().executeOnPooledThread {
      val matchingRefs = refs.stream().filter { ref -> ref.name.startsWith(reference)
                                                       && (repositoryRoot == null || ref.root == repositoryRoot) }.toList()
      ApplicationManager.getApplication().invokeLater {
        if (matchingRefs.isNotEmpty()) {
          val ref = matchingRefs.minWith(VcsGoToRefComparator(dataPack.logProviders))
          future.setFuture(jumpToCommit(ref.commitHash, ref.root, silently, focus))
          return@invokeLater
        }

        future.setFuture(jumpToHash(reference, silently, focus))
      }
    }
    return future
  }

  @Deprecated("Prefer using jumpToRefOrHash(repositoryRoot: VirtualFile?, reference: String, silently: Boolean, focus: Boolean)")
  @JvmStatic
  fun VcsLogUiEx.jumpToRefOrHash(reference: String, silently: Boolean, focus: Boolean): ListenableFuture<Boolean> {
    return jumpToRefOrHash(null, reference, silently, focus)
  }

  /**
   * Asynchronously selects the commit node defined by the given hash.
   *
   * @param commitHash target commit hash
   * @param silently   skip showing notification when the target is not found
   * @param focus      focus the table
   * @return future result (success or failure)
   */
  @JvmStatic
  fun VcsLogUiEx.jumpToHash(commitHash: String, silently: Boolean, focus: Boolean): ListenableFuture<Boolean> {
    val trimmedHash = StringUtil.trim(commitHash) { ch -> !StringUtil.containsChar("()'\"`", ch) }

    if (!VcsLogUtil.HASH_PREFIX_REGEX.matcher(trimmedHash).matches()) {
      if (!silently) {
        VcsNotifier.getInstance(logData.project).notifyWarning(VcsLogNotificationIdsHolder.NAVIGATION_ERROR, "",
                                                               VcsLogBundle.message("vcs.log.string.is.not.a.hash", commitHash))
      }
      return Futures.immediateFuture(false)
    }

    val future = SettableFuture.create<JumpResult>()
    jumpTo(trimmedHash, { visiblePack, partialHash -> getCommitRow(logData, visiblePack, partialHash) }, future, silently, focus)
    return mapToJumpSuccess(future)
  }

  /**
   * Asynchronously selects the given commit node.
   *
   * @param commitHash target commit hash
   * @param root       target commit repository root
   * @param silently   skip showing notification when the target is not found
   * @param focus      focus the table
   * @return future result (success or failure)
   */
  @JvmStatic
  fun VcsLogUiEx.jumpToCommit(commitHash: Hash, root: VirtualFile, silently: Boolean, focus: Boolean): ListenableFuture<Boolean> {
    return mapToJumpSuccess(jumpToCommitInternal(commitHash, root, silently, focus))
  }

  @JvmStatic
  private fun VcsLogUiEx.jumpToCommitSyncInternal(commitHash: Hash,
                                                  root: VirtualFile,
                                                  silently: Boolean,
                                                  focus: Boolean): JumpResult {
    return jumpToSync(commitHash, { visiblePack, hash ->
      if (!logData.storage.containsCommit(CommitId(hash, root))) return@jumpToSync VcsLogUiEx.COMMIT_NOT_FOUND
      getCommitRow(logData.storage, visiblePack, hash, root)
    }, silently, focus)
  }

  private fun VcsLogUiEx.jumpToCommitInternal(commitHash: Hash,
                                              root: VirtualFile,
                                              silently: Boolean,
                                              focus: Boolean): ListenableFuture<JumpResult> {
    val future = SettableFuture.create<JumpResult>()
    jumpTo(commitHash, { visiblePack, hash ->
      if (!logData.storage.containsCommit(CommitId(hash, root))) return@jumpTo VcsLogUiEx.COMMIT_NOT_FOUND
      getCommitRow(logData.storage, visiblePack, hash, root)
    }, future, silently, focus)
    return future
  }

  @JvmStatic
  @Deprecated("Reports cryptic message if 'silently == false'. Prefer using jumpToCommit(Hash, VirtualFile, ...)")
  fun VcsLogUiEx.jumpToCommit(commitIndex: VcsLogCommitStorageIndex, silently: Boolean, focus: Boolean): ListenableFuture<Boolean> {
    val future = SettableFuture.create<JumpResult>()
    jumpTo(commitIndex, { visiblePack, id ->
      if (visiblePack.dataPack is ErrorDataPack) return@jumpTo VcsLogUiEx.COMMIT_NOT_FOUND
      if (visiblePack is ErrorVisiblePack) return@jumpTo VcsLogUiEx.COMMIT_DOES_NOT_MATCH
      visiblePack.getCommitRow(id)
    }, future, silently, focus)
    return mapToJumpSuccess(future)
  }

  private fun getBranchRow(vcsLogData: VcsLogData, visiblePack: VisiblePack, referenceName: String, repositoryRoot: VirtualFile?): VcsLogVisibleGraphIndex {
    val matchingRefs = visiblePack.refs.branches.filter { ref -> ref.name == referenceName
                                                                 && (repositoryRoot == null || ref.root == repositoryRoot) }
    if (matchingRefs.isEmpty()) return VcsLogUiEx.COMMIT_NOT_FOUND

    val sortedRefs = matchingRefs.sortedWith(VcsGoToRefComparator(visiblePack.logProviders))
    for (ref in sortedRefs) {
      val branchRow = getCommitRow(vcsLogData.storage, visiblePack, ref.commitHash, ref.root)
      if (branchRow >= 0) return branchRow
    }
    return VcsLogUiEx.COMMIT_DOES_NOT_MATCH
  }

  private fun getCommitRow(vcsLogData: VcsLogData, visiblePack: VisiblePack, partialHash: String): VcsLogVisibleGraphIndex {
    if (partialHash.length == VcsLogUtil.FULL_HASH_LENGTH) {
      var row = VcsLogUiEx.COMMIT_NOT_FOUND
      val candidateHash = HashImpl.build(partialHash)
      for (candidateRoot in vcsLogData.roots) {
        if (vcsLogData.storage.containsCommit(CommitId(candidateHash, candidateRoot))) {
          val candidateRow = getCommitRow(vcsLogData.storage, visiblePack, candidateHash, candidateRoot)
          if (candidateRow >= 0) return candidateRow
          if (row == VcsLogUiEx.COMMIT_NOT_FOUND) row = candidateRow
        }
      }
      return row
    }
    val row = IntRef(VcsLogUiEx.COMMIT_NOT_FOUND)
    vcsLogData.storage.iterateCommits { candidate ->
      if (CommitIdByStringCondition.matches(candidate, partialHash)) {
        val candidateRow = getCommitRow(vcsLogData.storage, visiblePack, candidate.hash, candidate.root)
        if (row.get() == VcsLogUiEx.COMMIT_NOT_FOUND) row.set(candidateRow)
        return@iterateCommits candidateRow < 0
      }
      true
    }
    return row.get()
  }

  private fun getCommitRow(storage: VcsLogStorage, visiblePack: VisiblePack, hash: Hash, root: VirtualFile): VcsLogVisibleGraphIndex {
    if (visiblePack.dataPack is ErrorDataPack) return VcsLogUiEx.COMMIT_NOT_FOUND
    if (visiblePack is ErrorVisiblePack) return VcsLogUiEx.COMMIT_DOES_NOT_MATCH

    return visiblePack.getCommitRow(storage.getCommitIndex(hash, root))
  }

  private fun VisiblePack.getCommitRow(commitIndex: VcsLogCommitStorageIndex): VcsLogVisibleGraphIndex {
    val visibleGraphImpl = visibleGraph as? VisibleGraphImpl<VcsLogCommitStorageIndex> ?: return visibleGraph.getVisibleRowIndex(commitIndex)
                                                                                                 ?: VcsLogUiEx.COMMIT_DOES_NOT_MATCH
    val nodeId = visibleGraphImpl.permanentGraph.permanentCommitsInfo.getNodeId(commitIndex)
    if (nodeId == VcsLogUiEx.COMMIT_NOT_FOUND) return VcsLogUiEx.COMMIT_NOT_FOUND
    if (nodeId < 0) return VcsLogUiEx.COMMIT_DOES_NOT_MATCH
    return visibleGraphImpl.linearGraph.getNodeIndex(nodeId) ?: VcsLogUiEx.COMMIT_DOES_NOT_MATCH
  }

  private fun mapToJumpSuccess(future: ListenableFuture<JumpResult>): ListenableFuture<Boolean> {
    return Futures.transform(future, { it == JumpResult.SUCCESS }, MoreExecutors.directExecutor())
  }
}