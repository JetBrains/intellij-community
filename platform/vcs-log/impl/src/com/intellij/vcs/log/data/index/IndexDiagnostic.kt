// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.graph.api.LiteLinearGraph
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.utils.BfsWalk
import com.intellij.vcs.log.graph.utils.IntHashSetFlags
import com.intellij.vcs.log.graph.utils.LinearGraphUtils
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import it.unimi.dsi.fastutil.ints.IntArrayList

internal object IndexDiagnostic {
  private const val FILTERED_PATHS_LIMIT = 1000
  private const val COMMITS_TO_CHECK = 10

  fun IndexDataGetter.getDiffFor(commitsIdsList: List<Int>, commitDetailsList: List<VcsFullCommitDetails>): String {
    val report = StringBuilder()
    for ((commitId, commitDetails) in commitsIdsList.zip(commitDetailsList)) {
      getDiffFor(commitId, commitDetails)?.let { commitReport ->
        report.append(commitReport).append("\n")
      }
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
    val textFilter = details.fullMessage.lineSequence().first().takeIf { it.length > 3 }?.let {
      VcsLogFilterObject.fromPattern(it, false, true)
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
    }
    if (sb.isEmpty()) return null
    return sb.toString()
  }

  fun DataPack.getFirstCommits(storage: VcsLogStorage, roots: Collection<VirtualFile>): List<Int> {
    val rootsToCheck = roots.toMutableSet()
    val commitsToCheck = IntArrayList()

    @Suppress("UNCHECKED_CAST") val permanentGraphInfo = permanentGraph as? PermanentGraphInfo<Int> ?: return emptyList()
    val graph = LinearGraphUtils.asLiteLinearGraph(permanentGraphInfo.linearGraph)
    for (node in graph.nodesCount() - 1 downTo 0) {
      if (!graph.getNodes(node, LiteLinearGraph.NodeFilter.DOWN).isEmpty()) continue

      val root = storage.getCommitId(permanentGraphInfo.permanentCommitsInfo.getCommitId(node))?.root
      if (!rootsToCheck.remove(root)) continue

      // initial commit may not have files (in case of shallow clone), or it may have too many files
      // checking next commits instead
      BfsWalk(node, graph, IntHashSetFlags(COMMITS_TO_CHECK), false).walk { nextNode ->
        if (nextNode != node && graph.getNodes(nextNode, LiteLinearGraph.NodeFilter.DOWN).size == 1) {
          // skipping merge commits since they can have too many changes
          commitsToCheck.add(permanentGraphInfo.permanentCommitsInfo.getCommitId(nextNode))
        }
        return@walk commitsToCheck.size < COMMITS_TO_CHECK
      }
      if (rootsToCheck.isEmpty()) break
    }

    return commitsToCheck
  }
}