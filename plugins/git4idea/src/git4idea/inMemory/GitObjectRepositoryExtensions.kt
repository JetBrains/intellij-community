// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsException

import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.vcs.log.Hash
import git4idea.i18n.GitBundle
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.objects.Oid

/**
 * Finds a linear range of commits between two commit hashes.
 * Traverses the commit history from the [endCommit] back to the [startCommit].
 *
 * @throws VcsException if a merge commit is encountered during traversal or [startCommit] is not reached
 */
internal fun GitObjectRepository.findCommitsRange(
  startCommit: Hash,
  endCommit: Hash,
): List<GitObject.Commit> {
  var currentCommit = findCommit(Oid.fromHash(endCommit))
  val commits = mutableListOf(currentCommit)

  val commitOid = Oid.fromHash(startCommit)
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

/**
 * Chains commits sequentially on top of a base commit, preserving their original content
 * but updating parent references to form a linear history.
 */
internal suspend fun GitObjectRepository.chainCommits(base: Oid, commits: List<GitObject.Commit>): Oid {
  var currentNewCommit = base
  reportSequentialProgress(commits.size) { reporter ->
    for ((i, commit) in commits.withIndex()) {
      currentNewCommit = commitTreeWithOverrides(commit, parentsOids = listOf(currentNewCommit))

      reporter.itemStep()
    }
  }
  return currentNewCommit
}

/**
 * Rebases a commit onto a new parent by applying the commit's changes (diff from its original parent)
 * to the new parent's tree, preserving the original commit's metadata.
 */
internal fun GitObjectRepository.rebaseCommit(commit: GitObject.Commit, newParent: GitObject.Commit?): Oid {
  val tree = mergeTrees(commit, newParent)
  persistObject(tree)
  return commitTreeWithOverrides(commit, treeOid = tree.oid, parentsOids = newParent?.let { listOf(it.oid) } ?: emptyList())
}

/**
 * Performs a 3-way merge of trees to rebase a commit onto a new parent.
 * Uses the commit's original parent as the merge base, the new parent as "ours",
 * and the commit's tree as "theirs" to resolve conflicts.
 */
internal fun GitObjectRepository.mergeTrees(commit: GitObject.Commit, newParent: GitObject.Commit?): GitObject.Tree {
  require(commit.parentsOids.size <= 1) { "Rebasing merge commit is not allowed" }

  val ourTree = newParent?.let { findTree(it.treeOid) } ?: emptyTree
  val theirTree = findTree(commit.treeOid)
  val baseTree = commit.parentsOids.singleOrNull()?.let { findTree(findCommit(it).treeOid) } ?: emptyTree

  return mergeTrees(ourTree, theirTree, baseTree)
}

internal class MergeConflictException(
  val description: @NlsSafe String,
) : Exception("Merge conflict with git output:\n$description")

internal fun GitObjectRepository.getTreeFromEntry(entry: GitObject.Tree.Entry?): GitObject.Tree {
  return entry?.takeIf { it.mode == GitObject.Tree.FileMode.DIR }
           ?.let { findTree(it.oid) }
         ?: emptyTree
}