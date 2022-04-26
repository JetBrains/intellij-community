// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject

object IndexDiagnostic {
  private const val FILTERED_PATHS_LIMIT = 1000

  fun IndexDataGetter.getDiffFor(commitId: Int, commitDetails: VcsFullCommitDetails): String? {
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
    val textFilter = details.subject.takeIf { it.length > 3 }?.let {
      VcsLogFilterObject.fromPattern(it, false, true)
    }
    val pathsFilter = VcsLogFilterObject.fromPaths(details.parents.indices.flatMapTo(mutableSetOf()) { parentIndex ->
      ChangesUtil.getPaths(details.getChanges(parentIndex))
    }.take(FILTERED_PATHS_LIMIT))

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
}