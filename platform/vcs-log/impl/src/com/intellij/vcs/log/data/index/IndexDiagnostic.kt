// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.applyIf
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.graph.api.EdgeFilter
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.utils.BfsWalk
import com.intellij.vcs.log.graph.utils.IntHashSetFlags
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import it.unimi.dsi.fastutil.ints.IntOpenHashSet

internal object IndexDiagnostic {
  private const val FILTERED_PATHS_LIMIT = 1000
  private const val COMMITS_TO_CHECK = 10
  private const val INDEXED_COMMITS_ITERATIONS_LIMIT = 5_000

  fun IndexDataGetter.getDiffFor(commitsIdList: List<Int>, commitDetailsList: List<VcsFullCommitDetails>, checkAllCommits: Boolean = true): String {
    val report = StringBuilder()
    for ((commitId, commitDetails) in commitsIdList.zip(commitDetailsList)) {
      getDiffFor(commitId, commitDetails)?.let { commitReport ->
        report.append(commitReport).append("\n")
      }
      if (!checkAllCommits && report.isNotBlank()) return report.toString()
      ProgressManager.checkCanceled()
    }
    return report.toString()
  }

  private fun IndexDataGetter.getDiffFor(commitId: Int, commitDetails: VcsFullCommitDetails): String? {
    val difference = getCommitDetailsDiff(commitDetails, IndexedDetails(this, logStorage, commitId))
                     ?: getFilteringDiff(commitId, commitDetails)
    if (difference == null) return null
    return VcsLogBundle.message("vcs.log.index.diagnostic.error.for.commit", commitDetails.id.asString(), difference)
  }

  private fun getCommitDetailsDiff(expected: VcsCommitMetadata, actual: VcsCommitMetadata): String? {
    val sb = StringBuilder()

    sb.reportDiff(expected, actual, "vcs.log.index.diagnostic.error.attribute.name.author") { it.author }
    sb.reportDiff(expected, actual, "vcs.log.index.diagnostic.error.attribute.name.committer") { it.committer }
    sb.reportDiff(expected, actual, "vcs.log.index.diagnostic.error.attribute.name.author.time") { it.authorTime }
    sb.reportDiff(expected, actual, "vcs.log.index.diagnostic.error.attribute.name.committer.time") { it.commitTime }
    sb.reportDiff(expected, actual, "vcs.log.index.diagnostic.error.attribute.name.message") { it.fullMessage }
    sb.reportDiff(expected, actual, "vcs.log.index.diagnostic.error.attribute.name.parents") { it.parents }

    if (sb.isEmpty()) return null
    return sb.toString()
  }

  private fun StringBuilder.reportDiff(expectedMetadata: VcsCommitMetadata, actualMetadata: VcsCommitMetadata, attributeKey: String,
                                       attributeGetter: (VcsCommitMetadata) -> Any) {
    val expectedValue = attributeGetter(expectedMetadata)
    val actualValue = attributeGetter(actualMetadata)
    if (expectedValue == actualValue) return

    append(VcsLogBundle.message("vcs.log.index.diagnostic.error.message",
                                VcsLogBundle.message(attributeKey),
                                expectedValue, actualValue))
      .append("\n")
  }

  private fun IndexDataGetter.getFilteringDiff(commitId: Int, details: VcsFullCommitDetails): String? {
    val authorFilter = VcsLogFilterObject.fromUser(details.author)
    val textFilter = details.fullMessage.lineSequence().firstOrNull { it.length > 5 }?.let {
      VcsLogFilterObject.fromPattern(it.take(25), false, true)
    }
    val paths = details.parents.indices.flatMapTo(mutableSetOf()) { parentIndex ->
      ChangesUtil.getPaths(details.getChanges(parentIndex))
    }.take(FILTERED_PATHS_LIMIT)
    val pathsFilter = if (paths.isNotEmpty()) { VcsLogFilterObject.fromPaths(paths) } else null

    val sb = StringBuilder()
    for (filter in listOfNotNull(authorFilter, textFilter, pathsFilter)) {
      if (!filter(listOf(filter)).contains(commitId)) {
        sb.append(VcsLogBundle.message("vcs.log.index.diagnostic.error.filter", filter, details.id.toShortString()))
          .append("\n")
      }
      ProgressManager.checkCanceled()
    }
    if (sb.isEmpty()) return null
    return sb.toString()
  }

  fun DataPack.pickCommits(storage: VcsLogStorage, roots: Collection<VirtualFile>, old: Boolean): Set<Int> {
    val result = IntOpenHashSet()

    val rootsToCheck = roots.toMutableSet()
    @Suppress("UNCHECKED_CAST") val permanentGraphInfo = permanentGraph as? PermanentGraphInfo<Int> ?: return emptySet()
    val graph = LinearGraphUtils.asLiteLinearGraph(permanentGraphInfo.linearGraph)
    val nodeRange = (0 until graph.nodesCount()).applyIf<IntProgression>(old) { reversed() }
    for (node in nodeRange) {
      // looking for an initial commit or a branch head
      if (!graph.getNodes(node, if (old) LiteLinearGraph.NodeFilter.DOWN else LiteLinearGraph.NodeFilter.UP).isEmpty()) continue

      val root = storage.getCommitId(permanentGraphInfo.permanentCommitsInfo.getCommitId(node))?.root
      if (!rootsToCheck.remove(root)) continue

      // bfs walk from the node in order to get commits in the same root
      BfsWalk(node, graph, IntHashSetFlags(graph.nodesCount()), !old).walk { nextNode ->
        // skipping merge commits or initial commits
        // merge commits tend to have more changes
        // for shallow clones, initial commits have a lot of changes as well
        if (graph.getNodes(nextNode, LiteLinearGraph.NodeFilter.DOWN).size == 1) {
          result.add(permanentGraphInfo.permanentCommitsInfo.getCommitId(nextNode))
        }
        return@walk result.size < COMMITS_TO_CHECK
      }
      if (rootsToCheck.isEmpty()) break
    }

    return result
  }

  fun DataPack.pickIndexedCommits(dataGetter: IndexDataGetter, roots: Collection<VirtualFile>): Set<Int> {
    if (roots.isEmpty()) return emptySet()

    val result = IntOpenHashSet()

    // try to pick commits evenly across the graph
    @Suppress("UNCHECKED_CAST") val permanentGraphInfo = permanentGraph as? PermanentGraphInfo<Int> ?: return emptySet()
    for (i in 0 until COMMITS_TO_CHECK) {
      val node = i * (permanentGraphInfo.linearGraph.nodesCount() / COMMITS_TO_CHECK)
      if (permanentGraphInfo.linearGraph.getAdjacentEdges(node, EdgeFilter.NORMAL_DOWN).size != 1) continue
      val commit = permanentGraphInfo.permanentCommitsInfo.getCommitId(node)
      if (!dataGetter.indexStorageBackend.containsCommit(commit)) continue
      val root = dataGetter.logStorage.getCommitId(commit)?.root ?: continue
      if (!roots.contains(root)) continue
      result.add(commit)
    }

    if (result.size >= COMMITS_TO_CHECK) return result

    // iterate over a limited number of indexed commits to select more commits to check
    dataGetter.iterateIndexedCommits(INDEXED_COMMITS_ITERATIONS_LIMIT) { commit ->
      val node = permanentGraphInfo.permanentCommitsInfo.getNodeId(commit)
      if (node >= 0 && permanentGraphInfo.linearGraph.getAdjacentEdges(node, EdgeFilter.NORMAL_DOWN).size == 1) {
        val root = dataGetter.logStorage.getCommitId(commit)?.root
        if (root != null && roots.contains(root)) {
          result.add(commit)
        }
      }
      result.size < COMMITS_TO_CHECK
    }

    return result
  }
}