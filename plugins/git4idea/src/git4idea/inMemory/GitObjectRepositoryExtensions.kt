// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory

import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.i18n.GitBundle
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.objects.Oid

internal fun GitObjectRepository.findCommitsRange(
  commit: VcsCommitMetadata,
  initialHeadPosition: String,
): List<GitObject.Commit> {
  var currentCommit = findCommit(Oid.fromHex(initialHeadPosition))
  val commits = mutableListOf(currentCommit)

  val commitOid = Oid.fromHash(commit.id)
  while (currentCommit.oid != commitOid) {
    if (currentCommit.parentsOids.size != 1) {
      throw VcsException(GitBundle.message("rebase.log.multiple.commit.editing.action.specific.commit.root.or.merge",
                                           currentCommit.oid,
                                           currentCommit.parentsOids.size))
    }
    currentCommit = findCommit(currentCommit.parentsOids.single())
    commits.add(currentCommit)
  }
  return commits.reversed()
}

internal suspend fun GitObjectRepository.chainCommits(base: Oid, commits: List<GitObject.Commit>): Oid {
  var currentNewCommit = base
  reportRawProgress { reporter ->
    for ((i, commit) in commits.withIndex()) {
      currentNewCommit = commitTreeWithOverrides(commit, parentsOids = listOf(currentNewCommit))

      reporter.fraction((i + 1) / commits.size.toDouble())
    }
  }
  return currentNewCommit
}