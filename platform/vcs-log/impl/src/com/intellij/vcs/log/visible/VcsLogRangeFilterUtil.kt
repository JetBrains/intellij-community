// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.data.CommitIdByStringCondition
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.findBranch
import com.intellij.vcs.log.util.subgraphDifference
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet

private val LOG = Logger.getInstance("#com.intellij.vcs.log.visible.VcsLogRangeFilterUtil")

internal sealed class RangeFilterResult {
  class Commits(val commits: IntSet) : RangeFilterResult()
  data object InvalidRange : RangeFilterResult()
  data object Error : RangeFilterResult()
}

internal fun filterByRange(storage: VcsLogStorage,
                           logProviders: Map<VirtualFile, VcsLogProvider>,
                           dataPack: DataPack,
                           rangeFilter: VcsLogRangeFilter): RangeFilterResult {
  val set = IntOpenHashSet()
  for (range in rangeFilter.ranges) {
    var rangeResolvedAnywhere = false
    for ((root, _) in logProviders) {
      val resolvedRange = resolveCommits(storage, dataPack, root, range)
      if (resolvedRange != null) {
        val commits = getCommitsByRange(storage, dataPack, root, resolvedRange)
        if (commits == null) return RangeFilterResult.Error // error => will be handled by the VCS provider
        else set.addAll(commits)
        rangeResolvedAnywhere = true
      }
    }
    // If a range is resolved in some roots, but not all of them => skip others and handle those which know about the range.
    // Otherwise, if none of the roots know about the range => return null and let VcsLogProviders handle the range
    if (!rangeResolvedAnywhere) {
      LOG.warn("Range limits unresolved for: $range")
      return RangeFilterResult.InvalidRange
    }
  }
  return RangeFilterResult.Commits(set)
}

private fun getCommitsByRange(storage: VcsLogStorage, dataPack: DataPack, root: VirtualFile, range: Pair<CommitId, CommitId>): IntSet? {
  val fromIndex = storage.getCommitIndex(range.first.hash, root)
  val toIndex = storage.getCommitIndex(range.second.hash, root)

  return dataPack.subgraphDifference(toIndex, fromIndex)
}

private fun resolveCommits(vcsLogStorage: VcsLogStorage,
                           dataPack: DataPack,
                           root: VirtualFile,
                           range: VcsLogRangeFilter.RefRange): Pair<CommitId, CommitId>? {
  val from = resolveCommit(vcsLogStorage, dataPack, root, range.exclusiveRef) ?: run {
    LOG.debug { "Can not resolve ${range.exclusiveRef} in $root for range $range" }
    return null
  }
  val to = resolveCommit(vcsLogStorage, dataPack, root, range.inclusiveRef) ?: run {
    LOG.debug { "Can not resolve ${range.inclusiveRef} in $root for range $range"}
    return null
  }
  return from to to
}

private fun resolveCommit(storage: VcsLogStorage, dataPack: DataPack,
                          root: VirtualFile, refName: String): CommitId? {
  if (VcsLogUtil.isFullHash(refName)) {
    val commitId = CommitId(HashImpl.build(refName), root)
    return if (storage.containsCommit(commitId)) commitId else null
  }

  val ref = dataPack.refsModel.findBranch(refName, root)
  if (ref != null) return CommitId(ref.commitHash, root)
  if (refName.length >= VcsLogUtil.SHORT_HASH_LENGTH && VcsLogUtil.HASH_REGEX.matcher(refName).matches()) {
    // don't search for too short hashes: high probability to treat a ref, existing not in all roots, as a hash
    return storage.findCommitId(CommitIdByStringCondition(refName))
  }
  return null
}
