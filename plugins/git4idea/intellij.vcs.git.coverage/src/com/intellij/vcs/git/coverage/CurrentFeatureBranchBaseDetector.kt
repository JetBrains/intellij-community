// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.coverage

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.vcs.git.coverage.CurrentFeatureBranchBaseDetector.Status
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.utils.BfsWalk
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.GitRemoteBranch
import git4idea.config.GitSharedSettings
import git4idea.repo.GitRepository
import org.jetbrains.annotations.VisibleForTesting

internal class CurrentFeatureBranchBaseDetector(private val repository: GitRepository) {

  private val logData = VcsProjectLog.getInstance(repository.project).dataManager
  private val storage = logData?.storage
  private val pack = logData?.dataPack

  @Suppress("UNCHECKED_CAST")
  private val permanentGraph = pack?.permanentGraph as? PermanentGraphInfo<Int>

  fun findBaseCommit(): Status {
    val project = repository.project
    val remoteBranches = repository.branches.remoteBranches
    val protectedBranches = remoteBranches.filter { GitSharedSettings.getInstance(project).isBranchProtected(it.nameForRemoteOperations) }
    if (protectedBranches.isEmpty()) {
      // This filter can only be applied when there is a pushed protected branch.
      return Status.NoProtectedBranches
    }

    val permanentCommitsInfo = permanentGraph?.permanentCommitsInfo ?: return Status.GitDataNotFound
    val headHash = getHeadHash() ?: return Status.GitDataNotFound
    val protectedBranchHashes = protectedBranches.map { branch ->
      val branchHash = repository.branches.getHash(branch) ?: return Status.GitDataNotFound
      branch to branchHash
    }

    val headNodeId = getCommitIndex(headHash)
                       ?.let { permanentCommitsInfo.getNodeId(it) }
                       ?.takeIf { it >= 0 }
                     ?: return Status.GitDataNotFound
    val protectedBranchIndexes = protectedBranchHashes.mapNotNull { (branch, hash) ->
      val commitIndex = storage?.getCommitIndex(hash, repository.root) ?: return@mapNotNull null
      commitIndex to branch
    }.toMap()

    val allNodeIds = permanentCommitsInfo.convertToNodeIds(protectedBranchIndexes.keys)
    val protectedNodeIds = allNodeIds.mapNotNull { nodeId ->
      val commitIndex = permanentCommitsInfo.getCommitId(nodeId)
      val branch = protectedBranchIndexes[commitIndex] ?: return@mapNotNull null
      nodeId to branch
    }.toMap()

    if (protectedNodeIds.isEmpty()) return Status.GitDataNotFound

    val linearGraph = permanentGraph.linearGraph
    return when (val status = findBaseCommit(linearGraph, headNodeId, protectedNodeIds.keys)) {
      is Status.InternalSuccess -> {
        val commits = status.commits.map { (commitId, protectedBranchId) ->
          val hash = getHash(commitId) ?: return Status.GitDataNotFound
          val branch = protectedNodeIds[protectedBranchId] ?: return Status.GitDataNotFound
          BaseCommitAndBranch(hash, branch)
        }
        Status.Success(commits)
      }
      else -> status
    }
  }

  private fun getHeadHash(): Hash? {
    val headRevision = repository.currentRevision ?: return null
    return try {
      HashImpl.build(headRevision)
    }
    catch (e: Throwable) {
      if (e is ControlFlowException) {
        throw e
      }
      thisLogger().warn(e)
      return null
    }
  }

  private fun getCommitIndex(hash: Hash): Int? = storage?.getCommitIndex(hash, repository.root)

  private fun getHash(nodeId: Int): Hash? {
    val commitId = permanentGraph?.permanentCommitsInfo?.getCommitId(nodeId) ?: return null
    val commit = storage?.getCommitId(commitId)
    return commit?.hash
  }

  internal sealed interface Status {
    data class Success(val commits: List<BaseCommitAndBranch>) : Status
    data class InternalSuccess(val commits: List<BaseCommit>) : Status
    data object NoProtectedBranches : Status
    data object HeadInProtectedBranch : Status
    data object GitDataNotFound : Status
    data object CommitHasNoProtectedParents : Status
  }

  internal data class BaseCommit(val commitId: Int, val protectedNodeId: Int)
  internal data class BaseCommitAndBranch(val hash: Hash, val protectedBranch: GitRemoteBranch)
}

@VisibleForTesting
internal fun findBaseCommit(linearGraph: LinearGraph, headNodeId: Int, protectedNodeIds: Set<Int>): Status {
  val graph = LinearGraphUtils.asLiteLinearGraph(linearGraph)

  val visited = BitSetFlags(graph.nodesCount())
  if (findProtectedBranchNodeId(headNodeId, graph, visited, protectedNodeIds) != null) {
    // The current commit is already a part of a protected branch.
    // No feature branch filtering could be applied.
    return Status.HeadInProtectedBranch
  }
  else {
    visited.set(headNodeId, false)
  }

  val bfsWalk = BfsWalk(headNodeId, graph, visited)
  val foundCommits = mutableListOf<CurrentFeatureBranchBaseDetector.BaseCommit>()
  while (true) {
    val nextLayer = bfsWalk.step()
    if (nextLayer.isEmpty()) break
    val protectedCommits = hashSetOf<Int>()
    for (commit in nextLayer) {
      val protectedNodeId = findProtectedBranchNodeId(commit, graph, visited, protectedNodeIds) ?: continue
      foundCommits += CurrentFeatureBranchBaseDetector.BaseCommit(commit, protectedNodeId)
      protectedCommits.add(commit)
    }
    // reset visited marks to continue searching down
    for (nodeId in nextLayer) {
      if (nodeId in protectedCommits) continue
      visited.set(nodeId, false)
    }
  }
  if (foundCommits.isEmpty()) return Status.CommitHasNoProtectedParents
  return Status.InternalSuccess(foundCommits)
}

private fun findProtectedBranchNodeId(commitId: Int, linearGraph: LiteLinearGraph, visited: BitSetFlags, protectedNodeIds: Set<Int>): Int? {
  var protectedNodeId: Int? = null
  DfsWalk(listOf(commitId), linearGraph, visited).walk(goDown = false) { nodeId ->
    if (nodeId in protectedNodeIds) {
      protectedNodeId = nodeId
      false
    }
    else true
  }
  return protectedNodeId
}

