// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.IntCollectionUtil
import git4idea.GitUtil
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import java.util.regex.Pattern

fun IndexDataGetter.match(root: VirtualFile,
                          sourceBranchCommits: IntSet,
                          targetBranchCommits: IntSet,
                          reliable: Boolean = true): IntSet {
  val timeToSourceCommit = IntCollectionUtil.groupByAsIntSet(sourceBranchCommits) { getAuthorTime(it) }
  val authorToSourceCommit = IntCollectionUtil.groupByAsIntSet(sourceBranchCommits) { getAuthor(it) }

  val result = IntOpenHashSet()
  for (targetCommit in targetBranchCommits) {
    val time = getAuthorTime(targetCommit)
    val author = getAuthor(targetCommit)

    val commitsForAuthor = authorToSourceCommit[author] ?: IntOpenHashSet()
    val sourceCandidates = IntCollectionUtil.intersect(timeToSourceCommit[time] ?: IntOpenHashSet(), commitsForAuthor) ?: continue
    if (!sourceCandidates.isEmpty()) {
      result.addAll(selectSourceCommits(targetCommit, root, sourceCandidates, commitsForAuthor, reliable))
    }
  }

  return result
}

private const val suffixStart = "cherry picked from commit" //NON-NLS
private val suffixPattern = Pattern.compile("$suffixStart.*\\)")

private fun IndexDataGetter.selectSourceCommits(targetCommit: Int,
                                                root: VirtualFile,
                                                sourceCandidates: IntSet,
                                                sourceCandidatesExtended: IntSet,
                                                reliable: Boolean): IntSet {
  val targetMessage = getFullMessage(targetCommit) ?: return IntSet.of()

  val result = IntOpenHashSet()
  val matcher = suffixPattern.matcher(targetMessage)
  while (matcher.find()) {
    val match = targetMessage.subSequence(matcher.start(), matcher.end())
    val hashesString = match.subSequence(suffixStart.length, match.length - 1) // -1 for the last ")"
    val hashesCandidates = hashesString.split(",", " ", ";")
    for (h in hashesCandidates) {
      if (GitUtil.isHashString(h, false)) {
        val hash = HashImpl.build(h)
        val index = logStorage.getCommitIndex(hash, root)
        if (sourceCandidatesExtended.contains(index)) {
          result.add(index)
        }
      }
    }
    if (IntCollectionUtil.intersects(sourceCandidates, result)) return result // target time should match one of sources time
  }

  if (!reliable) {
    val inexactMatches = mutableSetOf<Int>()
    val exactMatches = mutableSetOf<Int>()
    for (sourceCandidate in sourceCandidates) {
      val sourceMessage = getFullMessage(sourceCandidate) ?: return IntSet.of()
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
      return IntSet.of(match)
    }
  }

  return IntSet.of()
}