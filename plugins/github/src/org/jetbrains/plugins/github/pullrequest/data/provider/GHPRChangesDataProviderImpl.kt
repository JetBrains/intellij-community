// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.google.common.graph.GraphBuilder
import com.google.common.graph.ImmutableGraph
import com.google.common.graph.Traverser
import com.intellij.collaboration.async.classAsCoroutineName
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.filePath
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHCommitHash
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRChangesService
import java.util.concurrent.CompletableFuture

internal class GHPRChangesDataProviderImpl(parentCs: CoroutineScope,
                                           private val changesService: GHPRChangesService,
                                           private val loadReferences: suspend () -> GHPRBranchesRefs,
                                           private val pullRequestId: GHPRIdentifier)
  : GHPRChangesDataProvider {
  private val cs = parentCs.childScope(classAsCoroutineName())

  private var requests: ChangesDataLoader? = null
  private val requestsGuard = Mutex()

  private val _changesNeedReloadSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  override val changesNeedReloadSignal: Flow<Unit> = _changesNeedReloadSignal.asSharedFlow()

  override suspend fun loadCommits(): List<GHCommit> {
    val refs = loadReferences()
    return getLoader(refs).loadCommits()
  }

  override suspend fun loadChanges(): GitBranchComparisonResult {
    val refs = loadReferences()
    return getLoader(refs).loadChanges()
  }

  private suspend fun getLoader(refs: GHPRBranchesRefs): ChangesDataLoader =
    requestsGuard.withLock {
      val current = requests
      val new = if (current == null) {
        ChangesDataLoader(cs, refs)
      }
      else if (current.refs != refs) {
        current.cancel()
        ChangesDataLoader(cs, refs)
      }
      else {
        current
      }
      requests = new
      new
    }

  override suspend fun signalChangesNeedReload() {
    requestsGuard.withLock {
      requests?.cancel()
      requests = null
    }
    _changesNeedReloadSignal.tryEmit(Unit)
  }

  //TODO: don't fetch when all revision already present
  override suspend fun ensureAllRevisionsFetched() {
    coroutineScope {
      launch {
        val refs = loadReferences()
        changesService.fetch(refs.baseRef)
      }
      launch {
        changesService.fetch("refs/pull/${pullRequestId.number}/head:")
      }
    }
  }

  override suspend fun loadPatchFromMergeBase(commitSha: String, filePath: String): FilePatch? {
    // cache merge base
    val refs = loadReferences()
    val mergeBase = changesService.loadMergeBaseOid(refs.baseRef, refs.headRef)
    return changesService.loadPatch(mergeBase, commitSha).find { it.filePath == filePath }
  }

  @Deprecated("Please migrate ro coroutines and use apiCommitsRequest")
  override fun loadCommitsFromApi(): CompletableFuture<List<GHCommit>> =
    cs.async { loadCommits() }.asCompletableFuture()

  private inner class ChangesDataLoader(parentCs: CoroutineScope, val refs: GHPRBranchesRefs) {
    private val cs = parentCs.childScope()

    private val referencesRequest = cs.async(start = CoroutineStart.LAZY) {
      val mergeBaseRef = changesService.loadMergeBaseOid(refs.baseRef, refs.headRef)
      val commits = changesService.loadCommitsFromApi(pullRequestId)
      val sortedCommits = sortCommits(commits, refs.headRef)
      AllPRReferences(refs.baseRef, mergeBaseRef, refs.headRef, sortedCommits)
    }

    private val changesRequest = cs.async(start = CoroutineStart.LAZY) {
      val references = referencesRequest.await()
      changesService.createChangesProvider(pullRequestId, references.baseRefOid, references.mergeBaseRefOid, references.headRefOid, references.commits)
    }

    suspend fun loadCommits(): List<GHCommit> = referencesRequest.await().commits
    suspend fun loadChanges(): GitBranchComparisonResult = changesRequest.await()

    fun cancel() {
      referencesRequest.cancel()
      changesRequest.cancel()
    }
  }
}

private data class AllPRReferences(
  val baseRefOid: String, val mergeBaseRefOid: String, val headRefOid: String, val commits: List<GHCommit>
)

// TODO: can we get rid of the tree?
private fun sortCommits(commits: Collection<GHCommit>, lastCommitSha: String): List<GHCommit> {
  val commitsBySha = mutableMapOf<String, GHCommit>()
  val parentCommits = mutableSetOf<GHCommitHash>()

  var lastCommit: GHCommit? = null
  for (commit in commits) {
    if (commit.oid == lastCommitSha) {
      lastCommit = commit
    }
    commitsBySha[commit.oid] = commit
    parentCommits.addAll(commit.parents)
  }
  checkNotNull(lastCommit) { "Could not determine last commit" }

  fun ImmutableGraph.Builder<GHCommit>.addCommits(commit: GHCommit) {
    addNode(commit)
    for (parent in commit.parents) {
      val parentCommit = commitsBySha[parent.oid]
      if (parentCommit != null) {
        putEdge(commit, parentCommit)
        addCommits(parentCommit)
      }
    }
  }

  val graph = GraphBuilder
    .directed()
    .allowsSelfLoops(false)
    .immutable<GHCommit>()
    .apply {
      addCommits(lastCommit)
    }.build()
  return Traverser.forGraph(graph).depthFirstPostOrder(lastCommit).toList()
}