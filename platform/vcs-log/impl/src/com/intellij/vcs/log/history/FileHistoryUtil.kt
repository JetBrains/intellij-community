// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vcs.history.VcsHistoryUtil
import com.intellij.openapi.vcs.vfs.VcsVirtualFile
import com.intellij.openapi.vcs.vfs.VcsVirtualFolder
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogDataPack
import com.intellij.vcs.log.VcsLogDiffHandler
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.history.FileHistoryPaths.filePath
import com.intellij.vcs.log.history.FileHistoryPaths.isDeletedInCommit
import com.intellij.vcs.log.impl.VcsChangesMerger
import com.intellij.vcs.log.visible.VisiblePack

object FileHistoryUtil {
  @JvmStatic
  fun createVcsVirtualFile(revision: VcsFileRevision?): VirtualFile? {
    if (!VcsHistoryUtil.isEmpty(revision)) {
      if (revision is VcsFileRevisionEx) {
        val path = revision.path
        return if (path.isDirectory)
          VcsVirtualFolder(path, null)
        else
          VcsVirtualFile(path, revision)
      }
    }
    return null
  }

  fun affectsFile(change: Change, file: FilePath, isDeleted: Boolean): Boolean {
    val revision = if (isDeleted) change.beforeRevision else change.afterRevision
    if (revision == null) return false
    return file == revision.file
  }

  fun affectsDirectory(change: Change, directory: FilePath): Boolean {
    return affectsDirectory(directory, change.afterRevision) || affectsDirectory(directory, change.beforeRevision)
  }

  private fun affectsDirectory(directory: FilePath, revision: ContentRevision?): Boolean {
    if (revision == null) return false
    return VfsUtilCore.isAncestor(directory.ioFile, revision.file.ioFile, false)
  }

  @JvmStatic
  fun createChangeToParents(
    commitRow: Int, parentRows: List<Int>,
    visiblePack: VisiblePack, diffHandler: VcsLogDiffHandler,
    logData: VcsLogData,
  ): Change? {
    val commitIndex = visiblePack.visibleGraph.getRowInfo(commitRow).commit
    val path = visiblePack.filePath(commitIndex)
    if (path == null) return null
    val commitHash = logData.getCommitId(commitIndex)!!.hash
    val afterRevision = createContentRevision(commitHash, commitIndex, visiblePack, diffHandler)

    if (parentRows.isEmpty()) {
      if (afterRevision == null) return null
      return Change(null, afterRevision)
    }

    val parentCommits = parentRows.map { visiblePack.visibleGraph.getRowInfo(it).commit }
    val parentHashes = parentCommits.map { logData.getCommitId(it)!!.hash }

    val parentChanges = parentCommits.zip(parentHashes).mapNotNull { (index, hash) ->
      val beforeRevision = createContentRevision(hash, index, visiblePack, diffHandler)
      if (afterRevision == null && beforeRevision == null) return@mapNotNull null
      Change(beforeRevision, afterRevision)
    }

    if (parentChanges.size <= 1) {
      return parentChanges.firstOrNull()
    }
    return MyVcsChangesMerger(commitHash, parentHashes, diffHandler).mergedChange(path, parentChanges)
  }

  private fun createContentRevision(
    commitHash: Hash, commitIndex: Int, visiblePack: VcsLogDataPack,
    diffHandler: VcsLogDiffHandler,
  ): ContentRevision? {
    val isDeleted = visiblePack.isDeletedInCommit(commitIndex)
    if (isDeleted) return null
    val path = visiblePack.filePath(commitIndex)
    if (path == null) return null
    return diffHandler.createContentRevision(path, commitHash)
  }

  private class MyVcsChangesMerger(
    private val commit: Hash,
    parentCommits: List<Hash>,
    private val diffHandler: VcsLogDiffHandler,
  ) : VcsChangesMerger() {
    private val firstParent: Hash = parentCommits.first()

    override fun createChange(type: Change.Type, beforePath: FilePath?, afterPath: FilePath?): Change {
      val beforeRevision = if (beforePath == null) null else diffHandler.createContentRevision(beforePath, firstParent)
      val afterRevision = if (afterPath == null) null else diffHandler.createContentRevision(afterPath, commit)
      return Change(beforeRevision, afterRevision)
    }
  }
}
