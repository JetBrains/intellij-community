// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.platform.vcs.impl.shared.log.VcsCopiedCommit
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogCommitSelection
import com.intellij.vcs.log.data.LoadingDetails
import com.intellij.vcs.log.util.VcsUserUtil

/**
 * The first [limit] selected commits, in the selection order, for the clipboard.
 * A commit with no cached metadata keeps only its hash and root.
 */
internal fun VcsLogCommitSelection.toCopiedCommits(limit: Int): List<VcsCopiedCommit> {
  require(limit >= 0) { "The copied commit limit must not be negative: $limit" }
  if (limit == 0) return emptyList()

  val selectedCommits = commits
  val commitCount = minOf(limit, selectedCommits.size)
  if (commitCount == 0) return emptyList()

  val metadata = cachedMetadata
  val copiedCommits = ArrayList<VcsCopiedCommit>(commitCount)
  for (index in 0 until commitCount) {
    copiedCommits.add(selectedCommits[index].toCopiedCommit(metadata.getOrNull(index)))
  }
  return copiedCommits
}

private fun CommitId.toCopiedCommit(metadata: VcsCommitMetadata?): VcsCopiedCommit {
  if (metadata == null || metadata is LoadingDetails) {
    return VcsCopiedCommit(hash = hash.asString(), rootPath = root.path)
  }

  val commitTime = metadata.commitTime
  return VcsCopiedCommit(
    hash = hash.asString(),
    rootPath = root.path,
    subject = metadata.subject,
    author = VcsUserUtil.getShortPresentation(metadata.author),
    commitTimeMs = if (commitTime > 0) commitTime else null,
  )
}
