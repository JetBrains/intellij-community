// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.coverage

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.git.coverage.CurrentFeatureBranchBaseDetector.Status
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.api.permanent.VcsLogGraphNodeId
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
import java.util.*

private data class CachedResult(val status: Status, val state: CachedState)
private data class CachedState(val headHash: String, val protectedBranchHashes: List<Pair<String, String>>)


@Service(Service.Level.PROJECT)
private class CurrentFeatureBranchBaseDetectorCache {
  val cache = WeakHashMap<GitRepository, CachedResult>()

  companion object {
    fun getInstance(project: Project) = project.service<CurrentFeatureBranchBaseDetectorCache>()
  }
}

internal class CurrentFeatureBranchBaseDetector(private val repository: GitRepository) {

  private val logData = VcsProjectLog.getInstance(repository.project).dataManager
  private val storage = logData?.storage
  private val pack = logData?.graphData

  @Suppress("UNCHECKED_CAST")
  private val permanentGraph = pack?.permanentGraph as? PermanentGraphInfo<VcsLogCommitStorageIndex>

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

    val currentState = CachedState(headHash.asString(), protectedBranchHashes.map { (branch, hash) ->
      branch.fullName to hash.asString()
    })

    val cache = CurrentFeatureBranchBaseDetectorCache.getInstance(project).cache
    val cachedResult = cache[repository]
    return if (cachedResult == null || cachedResult.state != currentState) {
      val (status, canCache) = computeStatus(headHash, permanentCommitsInfo, protectedBranchHashes)
      if (canCache) {
        cache[repository] = CachedResult(status, currentState)
      }
      else {
        cache.remove(repository)
      }
      status
    }
    else {
      cachedResult.status
    }
  }

  private fun computeStatus(
    headHash: Hash,
    permanentCommitsInfo: PermanentCommitsInfo<VcsLogCommitStorageIndex>,
    protectedBranchHashes: List<Pair<GitRemoteBranch, Hash>>,
  ): Pair<Status, Boolean> {
    val headNodeId = getCommitIndex(headHash)
                       ?.let { permanentCommitsInfo.getNodeId(it) }
                       ?.takeIf { it >= 0 }
                     ?: return Status.GitDataNotFound to false
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

    val completeGitData = protectedNodeIds.size == protectedBranchHashes.size
    if (protectedNodeIds.isEmpty()) return Status.GitDataNotFound to false

    val linearGraph = permanentGraph?.linearGraph ?: return Status.GitDataNotFound to false
    return when (val status = findBaseCommit(linearGraph, headNodeId, protectedNodeIds.keys)) {
      is Status.InternalSuccess -> {
        val commits = status.commits.map { (commitId, protectedBranchId) ->
          val hash = getHash(commitId) ?: return Status.GitDataNotFound to false
          val branch = protectedNodeIds[protectedBranchId] ?: return Status.GitDataNotFound to false
          BaseCommitAndBranch(hash, branch)
        }
        Status.Success(commits)
      }
      else -> status
    } to completeGitData
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
    data object SearchLimitReached : Status
  }

  internal data class BaseCommit(val commitId: Int, val protectedNodeId: Int)
  internal data class BaseCommitAndBranch(val hash: Hash, val protectedBranch: GitRemoteBranch)
}

@VisibleForTesting
internal fun findBaseCommit(linearGraph: LinearGraph, headNodeId: VcsLogGraphNodeId, protectedNodeIds: Set<VcsLogGraphNodeId>): Status {
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
  val searchLimit = Registry.intValue("coverage.git.log.commit.search.depth", 100)
  var searchDepth = 0
  while (true) {
    if (searchDepth++ > searchLimit) {
      // Return what have found up until now
      if (foundCommits.isNotEmpty()) break
      return Status.SearchLimitReached
    }
    val nextLayer = bfsWalk.step()
    if (nextLayer.isEmpty()) break
    val protectedCommits = hashSetOf<VcsLogCommitStorageIndex>()
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

private fun findProtectedBranchNodeId(nodeId: VcsLogGraphNodeId, linearGraph: LiteLinearGraph, visited: BitSetFlags, protectedNodeIds: Set<VcsLogGraphNodeId>): Int? {
  var protectedNodeId: Int? = null
  DfsWalk(listOf(nodeId), linearGraph, visited).walk(goDown = false) { nodeId ->
    if (nodeId in protectedNodeIds) {
      protectedNodeId = nodeId
      false
    }
    else true
  }
  return protectedNodeId
}

