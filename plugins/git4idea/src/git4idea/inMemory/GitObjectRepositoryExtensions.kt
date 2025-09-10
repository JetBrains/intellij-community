// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory

import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.i18n.GitBundle
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.objects.Oid

internal fun GitObjectRepository.findCommitsRange(
  commit: String,
  initialHeadPosition: String,
): List<GitObject.Commit> {
  var currentCommit = findCommit(Oid.fromHex(initialHeadPosition))
  val commits = mutableListOf(currentCommit)

  val commitOid = Oid.fromHex(commit)
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

internal fun GitObjectRepository.rebaseCommit(commit: GitObject.Commit, newParent: GitObject.Commit?): Oid {
  val tree = mergeTrees(commit, newParent)
  persistObject(tree)
  return commitTreeWithOverrides(commit, treeOid = tree.oid, parentsOids = newParent?.let { listOf(it.oid) } ?: emptyList())
}

internal fun GitObjectRepository.mergeTrees(commit: GitObject.Commit, newParent: GitObject.Commit?): GitObject.Tree {
  require(commit.parentsOids.size <= 1) { "Rebasing merge commit is not allowed" }

  val ourTree = newParent?.let { findTree(it.treeOid) } ?: emptyTree
  val theirTree = findTree(commit.treeOid)
  val baseTree = commit.parentsOids.singleOrNull()?.let { findTree(findCommit(it).treeOid) } ?: emptyTree

  return mergeTrees(ourTree, theirTree, baseTree)
}

/**
 * Trees should be persisted on disk
 */
@RequiresBackgroundThread
internal fun GitObjectRepository.mergeTrees(ours: GitObject.Tree, theirs: GitObject.Tree, base: GitObject.Tree): GitObject.Tree {
  val handler = GitLineHandler(repository.project, repository.root, GitCommand.MERGE_TREE).apply {
    setSilent(true)
    addParameters("--merge-base=${base.oid}")
    addParameters(ours.oid.hex(), theirs.oid.hex())
  }
  val result = Git.getInstance().runCommand(handler)
  if (result.exitCode == 1) {
    throw MergeConflictException(result.outputAsJoinedString)
  }
  result.throwOnError()

  return findTree(Oid.fromHex(result.outputAsJoinedString))
}

internal class MergeConflictException(
  val description: String,
) : Exception("Merge conflict with git output:\n$description")

internal fun GitObjectRepository.getTreeFromEntry(entry: GitObject.Tree.Entry?): GitObject.Tree {
  return entry?.takeIf { it.mode == GitObject.Tree.FileMode.DIR }
           ?.let { findTree(it.oid) }
         ?: emptyTree
}