// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.LinkDescriptor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.impl.facade.VisibleGraphImpl
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.graph.utils.isAncestor
import com.intellij.vcs.log.ui.table.links.CommitLinksProvider
import com.intellij.vcs.log.ui.table.links.CommitLinksResolveListener
import com.intellij.vcs.log.ui.table.links.NavigateToCommit
import com.intellij.vcs.log.visible.VisiblePack
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class GitCommitLinkProvider(private val project: Project) : CommitLinksProvider {
  override fun getLinks(commitId: CommitId): List<LinkDescriptor> {
    return project.service<GitLinkToCommitResolver>().getLinks(commitId)
  }

  override fun resolveLinks(logId: String, logData: VcsLogData, visiblePack: VisiblePack,
                            startRow: Int, endRow: Int) {
    project.service<GitLinkToCommitResolver>().submitResolveLinks(logId, logData, visiblePack,
                                                                  startRow, endRow)
  }
}

@Service(Service.Level.PROJECT)
internal class GitLinkToCommitResolver(private val project: Project) {

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

  private val resolveQueue = MergingUpdateQueue("resolve links queue", 100, true, null, project, null, false)
  private val updateQueue = MergingUpdateQueue("after resolve links ui update queue", 100, true, null, project, null, true)

  internal fun getLinks(commitId: CommitId): List<LinkDescriptor> {
    return getCachedOrEmpty(commitId).map { NavigateToCommit(it.range, it.targetHash) }
  }

  internal fun submitResolveLinks(
    logId: String,
    logData: VcsLogData,
    visiblePack: VisiblePack,
    startRow: Int,
    endRow: Int
  ) {
    if (visiblePack.visibleGraph !is VisibleGraphImpl) return

    val startFrom = max(0, startRow)
    val end = max(0, endRow)
    val rowRange = startFrom..end

    val processingCount = max(abs(Registry.intValue("vcs.log.render.commit.links.process.chunk")), rowRange.count())
    if (processingCount < 2) return

    val visibleGraph = visiblePack.visibleGraph

    resolveQueue.queue(Update.create(logId + startFrom) {
      for (i in rowRange) {
        val commitId = visibleGraph.getRowInfo(i).commit
        val commit = logData.commitMetadataCache.getCachedData(commitId) ?: continue
        resolveLinks(logData, visiblePack, commit.getCommitId(), commit.subject, processingCount)
      }
      updateQueue.queue(Update.create(logId) {
        project.messageBus.syncPublisher(CommitLinksResolveListener.TOPIC).onLinksResolved(logId)
      })
    })
  }

  @RequiresBackgroundThread
  internal fun resolveLinks(
    logData: VcsLogData,
    visiblePack: VisiblePack,
    commitId: CommitId, commitMessage: String,
    processingCount: Int
  ) {
    if (visiblePack.visibleGraph !is VisibleGraphImpl) return

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

        val targetHash = resolveHash(logData, visiblePack, commitId, rest, processingCount)
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
    visiblePack: VisiblePack,
    commitId: CommitId, commitMessage: String,
    processingCount: Int
  ): String? {
    val sourceCommitId = logData.getCommitIndex(commitId.hash, commitId.root)
    val visibleGraph = visiblePack.visibleGraph as VisibleGraphImpl
    val sourceCommitNodeIndex = visibleGraph.getVisibleRowIndex(sourceCommitId) ?: return null
    val liteLinearGraph = LinearGraphUtils.asLiteLinearGraph(visibleGraph.linearGraph)

    var foundData: VcsCommitMetadata? = null
    iterateCommits(logData, visiblePack, sourceCommitNodeIndex, processingCount) { currentData ->
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
    logData: VcsLogData, visiblePack: VisiblePack,
    startFromCommitIndex: Int,
    commitsCount: Int,
    consumer: (VcsCommitMetadata) -> Boolean
  ) {
    val visibleGraph = visiblePack.visibleGraph as VisibleGraphImpl
    val linearGraph = visibleGraph.linearGraph

    val walkDepth = min(commitsCount, visibleGraph.visibleCommitCount)
    var visitedNode = 0
    DfsWalk(listOf(startFromCommitIndex), linearGraph).walk(true) { currentNodeId ->

      val currentCommitIndex = visibleGraph.getRowInfo(currentNodeId).commit
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

  private fun VcsCommitMetadata.getCommitIndex(logData: VcsLogData): Int = logData.getCommitIndex(id, root)

  private fun VcsCommitMetadata.getCommitId(): CommitId = CommitId(id, root)

  private data class PrefixTarget(val range: TextRange, val targetHash: String)
}
