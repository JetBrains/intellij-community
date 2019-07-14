// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.util.text.TwosideTextDiffProvider
import com.intellij.diff.util.Range
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker

object LocalTrackerDiffUtil {
  @JvmStatic
  fun computeDifferences(tracker: LineStatusTracker<*>?,
                         document1: Document,
                         document2: Document,
                         changelistId: String,
                         textDiffProvider: TwosideTextDiffProvider,
                         indicator: ProgressIndicator,
                         handler: LocalTrackerDiffHandler): Runnable {
    if (tracker is SimpleLocalLineStatusTracker) {
      // partial changes are disabled for file (ex: it is marked as "unmodified")
      return handler.fallback()
    }

    val partialTracker = tracker as? PartialLocalLineStatusTracker
    if (partialTracker == null || document2 != tracker.document) {
      return handler.error() // DiffRequest is out of date
    }

    indicator.checkCanceled()
    val data = runReadAction {
      partialTracker.readLock {
        val isReleased = partialTracker.isReleased
        val isOperational = partialTracker.isOperational()
        val affectedChangelistIds = partialTracker.getAffectedChangeListsIds()

        if (!isOperational) {
          TrackerData(isReleased, affectedChangelistIds, null)
        }
        else {
          val ranges = partialTracker.getRanges()

          val localText = document2.immutableCharSequence
          val vcsText = document1.immutableCharSequence
          val trackerVcsText = partialTracker.vcsDocument.immutableCharSequence

          val diffData = TrackerDiffData(ranges, localText, vcsText, trackerVcsText)
          TrackerData(isReleased, affectedChangelistIds, diffData)
        }
      }
    }

    if (data.isReleased) {
      return handler.error() // DiffRequest is out of date
    }

    val diffData = data.diffData
    if (diffData?.ranges == null) {
      if (data.affectedChangelist.size == 1 && data.affectedChangelist.contains(changelistId)) {
        // tracker is waiting for initialisation
        // there are only one changelist, so it's safe to fallback to default logic
        return handler.fallbackWithProgress()
      }

      return handler.retryLater()
    }

    val ranges = diffData.ranges
    val isContentsEqual = ranges.isEmpty()

    if (!StringUtil.equals(diffData.vcsText, diffData.trackerVcsText)) {
      return handler.error() // DiffRequest is out of date
    }

    if (textDiffProvider.isHighlightingDisabled) {
      return handler.done(isContentsEqual, emptyList(), emptyList())
    }


    val linesRanges = ranges.map { range -> Range(range.vcsLine1, range.vcsLine2, range.line1, range.line2) }

    val newFragments = textDiffProvider.compare(diffData.vcsText, diffData.localText, linesRanges, indicator)!!

    val fragments = mutableListOf<LineFragment>()
    val fragmentsData = mutableListOf<LineFragmentData>()

    for (i in ranges.indices) {
      val localRange = ranges[i]
      val rangeFragments = newFragments[i]

      fragments.addAll(rangeFragments)

      val fragmentData = LineFragmentData(localRange.isExcludedFromCommit, localRange.changelistId)
      repeat(rangeFragments.size) { fragmentsData.add(fragmentData) }
    }

    return handler.done(isContentsEqual, fragments, fragmentsData)
  }

  interface LocalTrackerDiffHandler {
    fun done(isContentsEqual: Boolean,
             fragments: List<LineFragment>,
             fragmentsData: List<LineFragmentData>): Runnable

    fun retryLater(): Runnable
    fun fallback(): Runnable
    fun fallbackWithProgress(): Runnable
    fun error(): Runnable
  }

  data class LineFragmentData(val isExcluded: Boolean,
                              val changelistId: String)

  private data class TrackerData(val isReleased: Boolean,
                                 val affectedChangelist: List<String>,
                                 val diffData: TrackerDiffData?)

  private data class TrackerDiffData(val ranges: List<LocalRange>?,
                                     val localText: CharSequence,
                                     val vcsText: CharSequence,
                                     val trackerVcsText: CharSequence)
}
