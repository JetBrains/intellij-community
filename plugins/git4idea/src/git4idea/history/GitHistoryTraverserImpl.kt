// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.EventDispatcher
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.IndexDataGetter
import com.intellij.vcs.log.data.index.IndexedDetails.Companion.createMetadata
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.utils.BfsWalk
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.impl.BitSetFlags
import com.intellij.vcs.log.impl.TimedVcsCommitImpl
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.GitCommit
import git4idea.GitUtil
import git4idea.history.GitHistoryTraverser.Traverse
import git4idea.history.GitHistoryTraverser.TraverseCommitInfo
import java.util.*

internal class GitHistoryTraverserImpl(private val project: Project, private val logData: VcsLogData) : GitHistoryTraverser {
  private val requestedRootsIndexingListeners = EventDispatcher.create(RequestedRootsIndexingListener::class.java)

  private val indexListener = VcsLogIndex.IndexingFinishedListener {
    requestedRootsIndexingListeners.multicaster.indexingFinished(it)
  }

  init {
    logData.index.addListener(indexListener)
    Disposer.register(logData, this)
  }

  override fun toHash(id: TraverseCommitId) = logData.getCommitId(id)!!.hash

  private fun startSearch(
    start: Hash,
    root: VirtualFile,
    walker: (startId: TraverseCommitId, graph: LiteLinearGraph, visited: BitSetFlags, handler: (id: TraverseCommitId) -> Boolean) -> Unit,
    commitHandler: Traverse.(id: TraverseCommitInfo) -> Boolean
  ) {
    val dataPack = logData.dataPack
    val hashIndex = logData.getCommitIndex(start, root)

    val permanentGraph = dataPack.permanentGraph as PermanentGraphImpl<Int>

    val hashNodeId = permanentGraph.permanentCommitsInfo.getNodeId(hashIndex).takeIf { it != -1 }
                     ?: throw IllegalArgumentException("Hash '${start.asString()}' doesn't exist in repository: $root")
    val graph = LinearGraphUtils.asLiteLinearGraph(permanentGraph.linearGraph)
    val visited = BitSetFlags(graph.nodesCount())
    val traverse = TraverseImpl(this)
    walker(hashNodeId, graph, visited) {
      ProgressManager.checkCanceled()
      if (Disposer.isDisposed(this)) {
        throw ProcessCanceledException()
      }
      val commitId = permanentGraph.permanentCommitsInfo.getCommitId(it)
      val parents = graph.getNodes(it, LiteLinearGraph.NodeFilter.DOWN)
      traverse.commitHandler(TraverseCommitInfo(commitId, parents))
    }
    traverse.loadDetails()
  }

  override fun traverse(
    root: VirtualFile,
    start: GitHistoryTraverser.StartNode,
    type: GitHistoryTraverser.TraverseType,
    commitHandler: Traverse.(id: TraverseCommitInfo) -> Boolean
  ) {
    fun findBranchHash(branchName: String) =
      VcsLogUtil.findBranch(logData.dataPack.refsModel, root, branchName)?.commitHash
      ?: throw IllegalArgumentException("Branch '$branchName' doesn't exist in the repository: $root")

    val hash = when (start) {
      is GitHistoryTraverser.StartNode.CommitHash -> start.hash
      GitHistoryTraverser.StartNode.Head -> findBranchHash(GitUtil.HEAD)
      is GitHistoryTraverser.StartNode.Branch -> findBranchHash(start.branchName)
    }
    startSearch(
      hash,
      root,
      walker = { hashNodeId, graph, visited, handler ->
        when (type) {
          GitHistoryTraverser.TraverseType.DFS -> DfsWalk(listOf(hashNodeId), graph, visited).walk(true, handler)
          GitHistoryTraverser.TraverseType.BFS -> BfsWalk(hashNodeId, graph, visited).walk(handler)
        }
      },
      commitHandler = commitHandler
    )
  }

  override fun addIndexingListener(
    roots: Collection<VirtualFile>,
    disposable: Disposable,
    listener: GitHistoryTraverser.IndexingListener
  ) {
    val indexingListener = RequestedRootsIndexingListenerImpl(roots, this, listener)
    requestedRootsIndexingListeners.addListener(indexingListener, disposable)

    val indexedRoot = roots.firstOrNull { logData.index.isIndexed(it) } ?: return
    indexingListener.indexingFinished(indexedRoot)
  }

  override fun loadMetadata(ids: List<TraverseCommitId>): List<VcsCommitMetadata> =
    ids.groupBy { getRoot(it) }.map { (root, commits) ->
      GitLogUtil.collectMetadata(project, root, commits.map { toHash(it).asString() })
    }.flatten()


  override fun loadFullDetails(
    ids: List<TraverseCommitId>,
    requirements: GitCommitRequirements,
    fullDetailsHandler: (GitCommit) -> Unit
  ) {
    ids.groupBy { getRoot(it) }.forEach { (root, commits) ->
      GitLogUtil.readFullDetailsForHashes(project, root, commits.map { toHash(it).asString() }, requirements, Consumer {
        fullDetailsHandler(it)
      })
    }
  }

  override fun getCurrentUser(root: VirtualFile): VcsUser? = logData.currentUser[root]

  override fun dispose() {
    logData.index.removeListener(indexListener)
  }

  private fun getRoot(id: TraverseCommitId): VirtualFile = logData.getCommitId(id)!!.root

  private class IndexedRootImpl(
    private val traverser: GitHistoryTraverser,
    private val project: Project,
    override val root: VirtualFile,
    private val dataGetter: IndexDataGetter
  ) : GitHistoryTraverser.IndexedRoot {
    override fun filterCommits(filter: GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter): Collection<TraverseCommitId> {
      val logFilter = when (filter) {
        is GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter.Author -> VcsLogFilterObject.fromUser(filter.author)
        is GitHistoryTraverser.IndexedRoot.TraverseCommitsFilter.File -> VcsLogFilterObject.fromPaths(setOf(filter.file))
      }
      return dataGetter.filter(listOf(logFilter))
    }

    override fun loadTimedCommit(id: TraverseCommitId): TimedVcsCommit {
      val parents = dataGetter.getParents(id)!!
      val timestamp = dataGetter.getCommitTime(id)!!
      val hash = traverser.toHash(id)
      return TimedVcsCommitImpl(hash, parents, timestamp)
    }

    override fun loadMetadata(id: TraverseCommitId): VcsCommitMetadata {
      val storage = dataGetter.logStorage
      val factory = project.service<VcsLogObjectsFactory>()
      return createMetadata(id, dataGetter, storage, factory)!!
    }
  }

  private class RequestedRootsIndexingListenerImpl(
    private val requestedRoots: Collection<VirtualFile>,
    private val traverser: GitHistoryTraverserImpl,
    private val listener: GitHistoryTraverser.IndexingListener
  ) : RequestedRootsIndexingListener {
    override fun indexingFinished(root: VirtualFile) {
      val index = traverser.logData.index
      val dataGetter = index.dataGetter ?: return
      val indexedRoots = requestedRoots.filter { index.isIndexed(it) }.map {
        IndexedRootImpl(traverser, traverser.project, it, dataGetter)
      }
      if (indexedRoots.isNotEmpty()) {
        listener.indexedRootsUpdated(indexedRoots)
      }
    }
  }

  private interface RequestedRootsIndexingListener : VcsLogIndex.IndexingFinishedListener, EventListener

  private class TraverseImpl(private val traverser: GitHistoryTraverser) : Traverse {
    val requests = mutableListOf<Request>()

    override fun loadMetadataLater(id: TraverseCommitId, onLoad: (VcsCommitMetadata) -> Unit) {
      requests.add(Request.LoadMetadata(id, onLoad))
    }

    override fun loadFullDetailsLater(id: TraverseCommitId, requirements: GitCommitRequirements, onLoad: (GitCommit) -> Unit) {
      requests.add(Request.LoadFullDetails(id, requirements, onLoad))
    }

    fun loadDetails() {
      loadMetadata(requests.filterIsInstance<Request.LoadMetadata>())
      loadFullDetails(requests.filterIsInstance<Request.LoadFullDetails>())
    }

    private fun loadMetadata(loadMetadataRequests: List<Request.LoadMetadata>) {
      val commitsMetadata = traverser.loadMetadata(loadMetadataRequests.map { it.id }).associateBy { it.id }
      for (request in loadMetadataRequests) {
        val hash = traverser.toHash(request.id)
        val details = commitsMetadata[hash] ?: continue
        request.onLoad(details)
      }
    }

    private fun loadFullDetails(loadFullDetailsRequests: List<Request.LoadFullDetails>) {
      val handlers = mutableMapOf<Hash, MutableMap<GitCommitRequirements, MutableList<(GitCommit) -> Unit>>>()
      loadFullDetailsRequests.forEach { request ->
        traverser.toHash(request.id).let { hash ->
          val requirementsToHandlers = handlers.getOrPut(hash) { mutableMapOf() }
          requirementsToHandlers.getOrPut(request.requirements) { mutableListOf() }.add(request.onLoad)
        }
      }

      val requirementTypes = loadFullDetailsRequests.map { it.requirements }.distinct()
      requirementTypes.forEach { requirements ->
        traverser.loadFullDetails(loadFullDetailsRequests.map { it.id }, requirements) { details ->
          handlers[details.id]?.get(requirements)?.forEach { onLoad ->
            onLoad(details)
          }
        }
      }
    }

    sealed class Request(val id: TraverseCommitId) {
      class LoadMetadata(id: TraverseCommitId, val onLoad: (VcsCommitMetadata) -> Unit) : Request(id)
      class LoadFullDetails(id: TraverseCommitId, val requirements: GitCommitRequirements, val onLoad: (GitCommit) -> Unit) : Request(id)
    }
  }
}