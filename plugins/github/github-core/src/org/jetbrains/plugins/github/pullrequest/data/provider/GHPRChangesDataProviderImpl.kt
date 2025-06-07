// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.google.common.graph.GraphBuilder
import com.google.common.graph.ImmutableGraph
import com.google.common.graph.Traverser
import com.intellij.openapi.diff.impl.patch.FilePatch
import com.intellij.platform.util.coroutines.childScope
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.filePath
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRChangesService

internal class GHPRChangesDataProviderImpl(parentCs: CoroutineScope,
                                           private val changesService: GHPRChangesService,
                                           private val loadReferences: suspend () -> GHPRBranchesRefs,
                                           private val pullRequestId: GHPRIdentifier)
  : GHPRChangesDataProvider {
  private val cs = parentCs.childScope(javaClass.name)

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

  override suspend fun ensureAllRevisionsFetched() {
    val refs = loadReferences()
    return getLoader(refs).fetchRevisions()
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

  override suspend fun loadPatchFromMergeBase(commitSha: String, filePath: String): FilePatch? {
    // cache merge base
    val refs = loadReferences()
    val mergeBase = changesService.loadMergeBaseOid(refs.baseRef, refs.headRef)
    return changesService.loadPatch(mergeBase, commitSha).find { it.filePath == filePath }
  }

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

    private val fetchRequest = cs.async(start = CoroutineStart.LAZY) {
      val references = referencesRequest.await()
      val revisions = buildList {
        add(references.baseRefOid)
        references.commits.mapTo(this) { it.oid }
      }
      if (changesService.areAllRevisionsFetched(revisions)) return@async
      coroutineScope {
        launch {
          changesService.fetch(references.baseRefOid)
        }
        launch {
          changesService.fetch("refs/pull/${pullRequestId.number}/head:")
        }
      }
      check(changesService.areAllRevisionsFetched(revisions)) {
        "Missing some pull request revisions"
      }
    }

    suspend fun loadCommits(): List<GHCommit> = referencesRequest.await().commits
    suspend fun loadChanges(): GitBranchComparisonResult = changesRequest.await()
    suspend fun fetchRevisions() = fetchRequest.await()

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

  var lastCommit: GHCommit? = null
  for (commit in commits) {
    if (commit.oid == lastCommitSha) {
      lastCommit = commit
    }
    commitsBySha[commit.oid] = commit
  }
  checkNotNull(lastCommit) { "Could not determine last commit" }

  val processedCommits = mutableSetOf<String>()
  fun ImmutableGraph.Builder<GHCommit>.addCommits(commit: GHCommit) {
    val alreadyProcessed = !processedCommits.add(commit.oid)
    if (alreadyProcessed) return
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