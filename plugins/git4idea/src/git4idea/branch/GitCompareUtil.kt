// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.TroveUtil
import com.intellij.vcs.log.util.VcsLogUtil
import gnu.trove.TIntHashSet
import java.util.regex.Pattern

fun IndexDataGetter.match(root: VirtualFile,
                          sourceBranchCommits: TIntHashSet,
                          targetBranchCommits: TIntHashSet,
                          reliable: Boolean = true): TIntHashSet {
  val timeToSourceCommit = TroveUtil.group(sourceBranchCommits) { getAuthorTime(it) }
  val authorToSourceCommit = TroveUtil.group(sourceBranchCommits) { getAuthor(it) }

  val result = TIntHashSet()
  for (targetCommit in targetBranchCommits) {
    val time = getAuthorTime(targetCommit)
    val author = getAuthor(targetCommit)

    val commitsForAuthor = authorToSourceCommit[author] ?: TIntHashSet()
    val sourceCandidates = TroveUtil.intersect(timeToSourceCommit[time] ?: TIntHashSet(), commitsForAuthor)
    if (sourceCandidates.isNotEmpty()) {
      TroveUtil.addAll(result, selectSourceCommits(targetCommit, root, sourceCandidates, commitsForAuthor, reliable))
    }
  }

  return result
}

private const val suffixStart = "cherry picked from commit"
private val suffixPattern = Pattern.compile("$suffixStart.*\\)")

private fun IndexDataGetter.selectSourceCommits(targetCommit: Int,
                                                root: VirtualFile,
                                                sourceCandidates: Set<Int>,
                                                sourceCandidatesExtended: TIntHashSet,
                                                reliable: Boolean): Set<Int> {
  val targetMessage = getFullMessage(targetCommit) ?: return emptySet()

  val result = mutableSetOf<Int>()
  val matcher = suffixPattern.matcher(targetMessage)
  while (matcher.find()) {
    val match = targetMessage.subSequence(matcher.start(), matcher.end())
    val hashesString = match.subSequence(suffixStart.length, match.length - 1) // -1 for the last ")"
    val hashesCandidates = hashesString.split(",", " ", ";")
    for (h in hashesCandidates) {
      if (VcsLogUtil.HASH_REGEX.matcher(h).matches()) {
        val hash = HashImpl.build(h)
        val index = logStorage.getCommitIndex(hash, root)
        if (sourceCandidatesExtended.contains(index)) {
          result.add(index)
        }
      }
    }
    if (ContainerUtil.intersects(sourceCandidates, result)) return result // target time should match one of sources time
  }

  if (!reliable) {
    val inexactMatches = mutableSetOf<Int>()
    val exactMatches = mutableSetOf<Int>()
    for (sourceCandidate in sourceCandidates) {
      val sourceMessage = getFullMessage(sourceCandidate) ?: return emptySet()
      if (targetMessage.contains(sourceMessage)) {
        if (targetMessage.length == sourceMessage.length) {
          exactMatches.add(sourceCandidate)
        }
        else {
          inexactMatches.add(sourceCandidate)
        }
      }
    }
    val match = (if (exactMatches.isNotEmpty()) exactMatches else inexactMatches).singleOrNull()
    if (match != null) {
      return setOf(match)
    }
  }

  return emptySet()
}