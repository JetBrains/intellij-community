// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.LinkDescriptor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.update.DebouncedUpdates
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.VcsLogVisibleGraphIndex
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.isAncestor
import com.intellij.vcs.log.ui.table.GraphTableModel
import com.intellij.vcs.log.ui.table.VcsLogTableIndex
import com.intellij.vcs.log.ui.table.links.CommitLinksProvider
import com.intellij.vcs.log.ui.table.links.CommitLinksResolveListener
import com.intellij.vcs.log.ui.table.links.NavigateToCommit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

internal class GitCommitLinkProvider(private val project: Project) : CommitLinksProvider {
  override fun getLinks(commitId: CommitId): List<LinkDescriptor> {
    return project.service<GitLinkToCommitResolver>().getLinks(commitId)
  }

  override fun resolveLinks(logId: String, logData: VcsLogData, model: GraphTableModel,
                            startRow: VcsLogTableIndex, endRow: VcsLogTableIndex) {
    project.service<GitLinkToCommitResolver>().submitResolveLinks(logId, logData, model,
                                                                  startRow, endRow)
  }
}

@Service(Service.Level.PROJECT)
internal class GitLinkToCommitResolver(private val project: Project, cs: CoroutineScope) {

  companion object {
    private const val PREFIX_DELIMITER_LENGTH = 1
    private val prefixes = listOf("fixup!", "squash!", "amend!")
    private val regex = "^(${prefixes.joinToString("|")}) (.*)$".toRegex()

    private const val CACHE_MAX_SIZE = 1_000L
  }

  private val prefixesCache: Cache<CommitId, List<PrefixTarget>> =
    Caffeine.newBuilder()
      .maximumSize(CACHE_MAX_SIZE)
      .build()

  private val resolveLinksQueue =
    DebouncedUpdates.forScope<ResolveRequest>(cs, "resolve links queue", 100.milliseconds)
      .withContext(Dispatchers.Default)
      .runBatched { batch -> processResolveBatch(batch) }

  private val updateUiQueue =
    DebouncedUpdates.forScope<String>(cs, "after resolve links ui update queue", 100.milliseconds)
      .withContext(Dispatchers.UI)
      .runBatched { batch -> processUpdateUiBatch(batch) }

  private fun processResolveBatch(batch: List<ResolveRequest>) {
    val byLogId = batch.groupBy { it.logId }.mapValues { it.value.last() }
    for ((_, request) in byLogId) {
      processResolveRequest(request)
    }
  }

  private fun processUpdateUiBatch(batch: List<String>) {
    for (logId in batch.toSet()) {
      project.messageBus.syncPublisher(CommitLinksResolveListener.TOPIC).onLinksResolved(logId)
    }
  }

  internal fun getLinks(commitId: CommitId): List<LinkDescriptor> {
    return getCachedOrEmpty(commitId).map { NavigateToCommit(it.range, it.targetHash) }
  }

  internal fun submitResolveLinks(
    logId: String,
    logData: VcsLogData,
    model: GraphTableModel,
    startRow: VcsLogTableIndex,
    endRow: VcsLogTableIndex
  ) {
    val visibleGraph = model.visiblePack.visibleGraph as? VisibleGraphImpl ?: return

    val startFrom = max(0, startRow)
    val end = max(0, endRow)
    val rowRange = startFrom..end

    val processingCount = max(abs(Registry.intValue("vcs.log.render.commit.links.process.chunk")), rowRange.count())
    if (processingCount < 2) return

    resolveLinksQueue.queue(ResolveRequest(logId, logData, model, visibleGraph, startFrom, end, processingCount))
  }

  private data class ResolveRequest(
    val logId: String,
    val logData: VcsLogData,
    val model: GraphTableModel,
    val visibleGraph: VisibleGraphImpl<VcsLogCommitStorageIndex>,
    val startRow: VcsLogTableIndex,
    val endRow: VcsLogTableIndex,
    val processingCount: Int
  )

  private fun processResolveRequest(request: ResolveRequest) {
    for (i in request.startRow..request.endRow) {
      val commitIndex = request.model.getId(i) ?: continue
      val commit = request.logData.commitMetadataCache.getCachedData(commitIndex) ?: continue
      resolveLinks(request.logData, request.visibleGraph, commit.getCommitId(), commit.subject, request.processingCount)
    }

    updateUiQueue.queue(request.logId)
  }

  @RequiresBackgroundThread
  internal fun resolveLinks(
    logData: VcsLogData,
    visibleGraph: VisibleGraphImpl<VcsLogCommitStorageIndex>,
    commitId: CommitId, commitMessage: String,
    processingCount: Int
  ) {
    val cachedPrefixes = getCachedOrEmpty(commitId)
    if (cachedPrefixes.isNotEmpty()) {
      return
    }

    var match = regex.matchEntire(commitMessage) ?: return
    var prefix = match.groups[1]?.value.orEmpty()
    var rest = match.groups[2]?.value.orEmpty()
    if (prefix.isBlank() && rest.isBlank()) return

    var prefixOffset = 0
    val existingPrefixes = cachedPrefixes.toMutableList()

    while (prefix.isNotBlank() && rest.isNotBlank()) {

      if (prefix.isNotBlank()) {
        val prefixRange = TextRange.from(prefixOffset, prefix.length)

        val targetHash = resolveHash(logData, visibleGraph, commitId, rest, processingCount)
        if (targetHash != null) {
          existingPrefixes.add(PrefixTarget(prefixRange, targetHash))
        }

        prefixOffset += prefix.length + PREFIX_DELIMITER_LENGTH
      }

      match = regex.matchEntire(rest) ?: break
      prefix = match.groups[1]?.value.orEmpty()
      rest = match.groups[2]?.value.orEmpty()
    }

    if (existingPrefixes.isNotEmpty()) {
      prefixesCache.put(commitId, existingPrefixes)
    }
  }

  private fun resolveHash(
    logData: VcsLogData,
    visibleGraph: VisibleGraphImpl<VcsLogCommitStorageIndex>,
    commitId: CommitId, commitMessage: String,
    processingCount: Int
  ): String? {
    val sourceCommitId = logData.getCommitIndex(commitId.hash, commitId.root)
    val sourceCommitNodeIndex = visibleGraph.getVisibleRowIndex(sourceCommitId) ?: return null
    val liteLinearGraph = LinearGraphUtils.asLiteLinearGraph(visibleGraph.linearGraph)

    var foundData: VcsCommitMetadata? = null
    iterateCommits(logData, visibleGraph, sourceCommitNodeIndex, processingCount) { currentData ->
      val currentNodeId = visibleGraph.getVisibleRowIndex(currentData.getCommitIndex(logData))
      if (currentNodeId != null && currentData.subject == commitMessage
          && liteLinearGraph.isAncestor(currentNodeId, sourceCommitNodeIndex)
      ) {
        foundData = currentData
      }

      foundData != null
    }

    return foundData?.id?.toString()
  }

  private fun iterateCommits(
    logData: VcsLogData,
    visibleGraph: VisibleGraphImpl<VcsLogCommitStorageIndex>,
    startFromCommitIndex: VcsLogVisibleGraphIndex,
    commitsCount: Int,
    consumer: (VcsCommitMetadata) -> Boolean
  ) {
    val linearGraph = visibleGraph.linearGraph

    val walkDepth = min(commitsCount, visibleGraph.visibleCommitCount)
    var visitedNode = 0
    DfsWalk(listOf(startFromCommitIndex), linearGraph).walk(true) { currentRow ->

      val currentCommitIndex = visibleGraph.getRowInfo(currentRow).commit
      val currentCommitData = logData.commitMetadataCache.getCachedData(currentCommitIndex)
      var consumed = false

      visitedNode++
      if (visitedNode > walkDepth) return@walk false

      if (currentCommitData != null) {
        if (currentCommitData.parents.size > 1) return@walk false

        consumed = consumer(currentCommitData)
      }

      !consumed
    }
  }

  private fun getCachedOrEmpty(commitId: CommitId): List<PrefixTarget> {
    return prefixesCache.getIfPresent(commitId) ?: emptyList()
  }

  private fun VcsCommitMetadata.getCommitIndex(logData: VcsLogData): VcsLogCommitStorageIndex = logData.getCommitIndex(id, root)

  private fun VcsCommitMetadata.getCommitId(): CommitId = CommitId(id, root)

  private data class PrefixTarget(val range: TextRange, val targetHash: String)
}
