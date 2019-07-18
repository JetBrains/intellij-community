// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.branch.GitBranchPair
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import java.util.*

private val LOG = logger<GitUpdatedRanges>()

class GitUpdatedRanges private constructor(
  private val project: Project,
  private val sourceAndTargetBranches: Map<GitRepository, GitBranchPair>) {

  private val initialPositions = calcPublishedTipPositions(sourceAndTargetBranches)

  fun calcCurrentPositions(): Map<GitRepository, HashRange> {
    val newPositions = calcPublishedTipPositions(sourceAndTargetBranches)
    val result = LinkedHashMap<GitRepository, HashRange>()
    for ((repository, newHash) in newPositions.entries) {
      val before = initialPositions[repository]
      if (before != null) {
        result[repository] = HashRange(before, newHash)
      }
    }
    return result
  }

  private fun calcPublishedTipPositions(trackedBranches: Map<GitRepository, GitBranchPair>): Map<GitRepository, Hash> {
    val result = LinkedHashMap<GitRepository, Hash>()
    for ((repository, branchPair) in trackedBranches.entries) {
      val (localBranch, trackedBranch) = branchPair
      val mergeBase = getMergeBase(repository.root, localBranch.fullName, trackedBranch.fullName)
      if (mergeBase != null) {
        result[repository] = mergeBase
      }
    }
    return result
  }

  private fun getMergeBase(root: VirtualFile, firstRef: String, secondRef: String): Hash? {
    val h = GitLineHandler(project, root, GitCommand.MERGE_BASE)
    h.addParameters(firstRef, secondRef)
    try {
      val output = Git.getInstance().runCommand(h).getOutputOrThrow().trim()
      return HashImpl.build(output)
    }
    catch (t: Throwable) {
      LOG.warn("Couldn't find merge-base between $firstRef and $secondRef")
      return null
    }
  }

  companion object {
    @JvmStatic
    fun calcInitialPositions(project: Project, trackedBranches: Map<GitRepository, GitBranchPair>): GitUpdatedRanges {
      return GitUpdatedRanges(project, trackedBranches)
    }
  }
}