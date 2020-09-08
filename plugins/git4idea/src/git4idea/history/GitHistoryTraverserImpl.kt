// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.utils.BfsWalk
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.impl.TimedVcsCommitImpl
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.GitCommit
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.history.GitHistoryTraverser.Traverse

class GitHistoryTraverserImpl(
  private val project: Project,
  override val root: VirtualFile,
  private val logData: VcsLogData,
  private val dataGetter: IndexDataGetter,
  private val requirements: GitCommitRequirements = GitCommitRequirements.DEFAULT
) : GitHistoryTraverser {
  override fun toHash(id: TraverseCommitId) = logData.storage.getCommitId(id)?.takeIf { it.root == root }!!.hash

  private fun startSearch(
    start: Hash,
    walker: (startId: TraverseCommitId, graph: LinearGraph, handler: (id: TraverseCommitId) -> Boolean) -> Unit,
    commitHandler: Traverse.(id: TraverseCommitId) -> Boolean
  ) {
    val dataPack = logData.dataPack
    val hashIndex = logData.getCommitIndex(start, root)

    val permanentGraph = dataPack.permanentGraph as PermanentGraphImpl<Int>

    val hashNodeId = permanentGraph.permanentCommitsInfo.getNodeId(hashIndex)
    val traverse = TraverseImpl(this)
    walker(hashNodeId, permanentGraph.linearGraph) {
      ProgressManager.checkCanceled()
      val commitId = permanentGraph.permanentCommitsInfo.getCommitId(it)
      traverse.commitHandler(commitId)
    }
    traverse.loadDetails()
  }

  override fun traverse(
    start: Hash,
    type: GitHistoryTraverser.TraverseType,
    commitHandler: Traverse.(id: TraverseCommitId) -> Boolean
  ) = startSearch(
    start,
    walker = { hashNodeId, graph, handler ->
      when (type) {
        GitHistoryTraverser.TraverseType.DFS -> DfsWalk(listOf(hashNodeId), graph).walk(true, handler)
        GitHistoryTraverser.TraverseType.BFS -> BfsWalk(hashNodeId, LinearGraphUtils.asLiteLinearGraph(graph)).walk(handler)
      }
    },
    commitHandler = commitHandler
  )

  override fun traverseFromHead(
    type: GitHistoryTraverser.TraverseType,
    commitHandler: Traverse.(id: TraverseCommitId) -> Boolean
  ) {
    val headRef = VcsLogUtil.findBranch(logData.dataPack.refsModel, root, GitUtil.HEAD)
    return traverse(headRef!!.commitHash, type, commitHandler)
  }

  override fun filterCommits(filter: GitHistoryTraverser.TraverseCommitsFilter): Collection<TraverseCommitId> {
    val logFilter = when (filter) {
      is GitHistoryTraverser.TraverseCommitsFilter.Author -> VcsLogFilterObject.fromUser(filter.author)
      is GitHistoryTraverser.TraverseCommitsFilter.File -> VcsLogFilterObject.fromPaths(setOf(filter.file))
    }
    return dataGetter.filter(listOf(logFilter))
  }

  override fun loadTimedCommit(id: TraverseCommitId): TimedVcsCommit {
    val parents = dataGetter.getParents(id)!!
    val timestamp = dataGetter.getAuthorTime(id)!!
    val hash = toHash(id)
    return TimedVcsCommitImpl(hash, parents, timestamp)
  }

  override fun loadMetadata(ids: List<TraverseCommitId>): List<VcsCommitMetadata> =
    GitLogUtil.collectMetadata(project, GitVcs.getInstance(project), root, ids.map { toHash(it).asString() })

  override fun loadFullDetails(ids: List<TraverseCommitId>, fullDetailsHandler: (GitCommit) -> Unit) {
    GitLogUtil.readFullDetailsForHashes(project, root, ids.map { toHash(it).asString() }, requirements, Consumer<GitCommit> {
      fullDetailsHandler(it)
    })
  }

  private class TraverseImpl(private val traverser: GitHistoryTraverserImpl) : Traverse {
    val requests = mutableListOf<Request>()

    override fun loadMetadataLater(id: TraverseCommitId, onLoad: (VcsCommitMetadata) -> Unit) {
      requests.add(Request.LoadMetadata(id, onLoad))
    }

    override fun loadFullDetailsLater(id: TraverseCommitId, onLoad: (GitCommit) -> Unit) {
      requests.add(Request.LoadFullDetails(id, onLoad))
    }

    fun loadDetails() {
      loadMetadata(requests.filterIsInstance<Request.LoadMetadata>())
      loadFullDetails(requests.filterIsInstance<Request.LoadFullDetails>())
    }

    private fun loadMetadata(loadMetadataRequests: List<Request.LoadMetadata>) {
      val commitsMetadata: Map<Hash, VcsCommitMetadata> = traverser.loadMetadata(loadMetadataRequests.map { it.id }).associateBy { it.id }
      for (request in loadMetadataRequests) {
        val hash = traverser.toHash(request.id)
        val details = commitsMetadata[hash] ?: continue
        request.onLoad(details)
      }
    }

    private fun loadFullDetails(loadFullDetailsRequests: List<Request.LoadFullDetails>) {
      val handlers = mutableMapOf<Hash, MutableList<(GitCommit) -> Unit>>()
      loadFullDetailsRequests.forEach { request ->
        traverser.toHash(request.id).let { hash ->
          handlers.getOrPut(hash) { mutableListOf() }.add(request.onLoad)
        }
      }
      traverser.loadFullDetails(loadFullDetailsRequests.map { it.id }) { details ->
        handlers[details.id]?.forEach { onLoad ->
          onLoad(details)
        }
      }
    }

    sealed class Request(val id: TraverseCommitId) {
      class LoadMetadata(id: TraverseCommitId, val onLoad: (VcsCommitMetadata) -> Unit) : Request(id)
      class LoadFullDetails(id: TraverseCommitId, val onLoad: (GitCommit) -> Unit) : Request(id)
    }
  }
}