// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.util.TroveUtil
import gnu.trove.TIntHashSet

fun IndexDataGetter.match(sourceBranchCommits: TIntHashSet, targetBranchCommits: TIntHashSet): TIntHashSet {
  val timeToSourceCommit = TroveUtil.group(sourceBranchCommits) { getAuthorTime(it) }
  val authorToSourceCommit = TroveUtil.group(sourceBranchCommits) { getAuthor(it) }

  val result = TIntHashSet()
  for (targetCommit in targetBranchCommits) {
    val time = getAuthorTime(targetCommit)
    val author = getAuthor(targetCommit)

    val sourceCandidates = TroveUtil.intersect(timeToSourceCommit[time] ?: TIntHashSet(),
                                               authorToSourceCommit[author] ?: TIntHashSet())
    if (sourceCandidates.isNotEmpty()) {
      selectSourceCommit(targetCommit, sourceCandidates)?.let { sourceCommit ->
        result.add(sourceCommit)
      }
    }
  }

  return result
}

private fun IndexDataGetter.selectSourceCommit(targetCommit: Int, sourceCandidates: Set<Int>): Int? {
  val targetMessage = getFullMessage(targetCommit) ?: return null
  for (sourceCandidate in sourceCandidates) {
    val sourceHash = logStorage.getCommitId(sourceCandidate)?.hash ?: continue
    if (targetMessage.contains("cherry picked from commit ${sourceHash.asString()}") ||
        targetMessage.contains("cherry picked from commit ${sourceHash.toShortString()}")) {
      return sourceCandidate
    }
  }

  return null
}
