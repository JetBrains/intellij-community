// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.util.TroveUtil
import gnu.trove.TIntHashSet

fun IndexDataGetter.match(sourceBranchCommits: TIntHashSet, targetBranchCommits: TIntHashSet, reliable: Boolean = true): TIntHashSet {
  val timeToSourceCommit = TroveUtil.group(sourceBranchCommits) { getAuthorTime(it) }
  val authorToSourceCommit = TroveUtil.group(sourceBranchCommits) { getAuthor(it) }

  val result = TIntHashSet()
  for (targetCommit in targetBranchCommits) {
    val time = getAuthorTime(targetCommit)
    val author = getAuthor(targetCommit)

    val sourceCandidates = TroveUtil.intersect(timeToSourceCommit[time] ?: TIntHashSet(),
                                               authorToSourceCommit[author] ?: TIntHashSet())
    if (sourceCandidates.isNotEmpty()) {
      selectSourceCommit(targetCommit, sourceCandidates, reliable)?.let { sourceCommit ->
        result.add(sourceCommit)
      }
    }
  }

  return result
}

private fun IndexDataGetter.selectSourceCommit(targetCommit: Int, sourceCandidates: Set<Int>, reliable: Boolean): Int? {
  val targetMessage = getFullMessage(targetCommit) ?: return null
  for (sourceCandidate in sourceCandidates) {
    val sourceHash = logStorage.getCommitId(sourceCandidate)?.hash ?: continue
    if (targetMessage.contains("cherry picked from commit ${sourceHash.asString()}") ||
        targetMessage.contains("cherry picked from commit ${sourceHash.toShortString()}")) {
      return sourceCandidate
    }
  }

  if (!reliable) {
    val inexactMatches = mutableSetOf<Int>()
    val exactMatches = mutableSetOf<Int>()
    for (sourceCandidate in sourceCandidates) {
      val sourceMessage = getFullMessage(sourceCandidate) ?: return null
      if (targetMessage.contains(sourceMessage)) {
        if (targetMessage.length == sourceMessage.length) {
          exactMatches.add(sourceCandidate)
        }
        else {
          inexactMatches.add(sourceCandidate)
        }
      }
    }
    if (exactMatches.isNotEmpty()) return exactMatches.singleOrNull()
    return inexactMatches.singleOrNull()
  }

  return null
}
