// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.util.base.DiffViewerBase
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
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager

object LocalTrackerDiffUtil {
  @JvmStatic
  fun computeDifferences(tracker: LineStatusTracker<*>?,
                         document1: Document,
                         document2: Document,
                         activeChangelistId: String,
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
      if (data.affectedChangelist.size == 1 && data.affectedChangelist.contains(activeChangelistId)) {
        // tracker is waiting for initialisation
        // there are only one changelist, so it's safe to fallback to default logic
        return handler.fallbackWithProgress()
      }

      return handler.retryLater()
    }

    val ranges = diffData.ranges
    val isContentsEqual = ranges.isEmpty()
    val texts = arrayOf(diffData.vcsText, diffData.localText)

    if (!StringUtil.equals(diffData.vcsText, diffData.trackerVcsText)) {
      return handler.error() // DiffRequest is out of date
    }

    if (textDiffProvider.isHighlightingDisabled) {
      return handler.done(isContentsEqual, texts, emptyList(), emptyList())
    }


    val linesRanges = ranges.map { range -> Range(range.vcsLine1, range.vcsLine2, range.line1, range.line2) }

    val newFragments = textDiffProvider.compare(diffData.vcsText, diffData.localText, linesRanges, indicator)!!

    val fragments = mutableListOf<LineFragment>()
    val fragmentsData = mutableListOf<LineFragmentData>()

    for (i in ranges.indices) {
      val localRange = ranges[i]
      val rangeFragments = newFragments[i]

      fragments.addAll(rangeFragments)

      val fragmentData = LineFragmentData(activeChangelistId, localRange.isExcludedFromCommit, localRange.changelistId)
      repeat(rangeFragments.size) { fragmentsData.add(fragmentData) }
    }

    return handler.done(isContentsEqual, texts, fragments, fragmentsData)
  }

  interface LocalTrackerDiffHandler {
    fun done(isContentsEqual: Boolean,
             texts: Array<CharSequence>,
             fragments: List<LineFragment>,
             fragmentsData: List<LineFragmentData>): Runnable

    fun retryLater(): Runnable
    fun fallback(): Runnable
    fun fallbackWithProgress(): Runnable
    fun error(): Runnable
  }

  data class LineFragmentData(val activeChangelistId: String,
                              val isExcludedFromCommit: Boolean,
                              val changelistId: String) {
    fun isFromActiveChangelist() = changelistId == activeChangelistId
    fun isSkipped() = !isFromActiveChangelist()
    fun isExcluded(allowExcludeChangesFromCommit: Boolean) = !isFromActiveChangelist() ||
                                                             allowExcludeChangesFromCommit && isExcludedFromCommit
  }

  private data class TrackerData(val isReleased: Boolean,
                                 val affectedChangelist: List<String>,
                                 val diffData: TrackerDiffData?)

  private data class TrackerDiffData(val ranges: List<LocalRange>?,
                                     val localText: CharSequence,
                                     val vcsText: CharSequence,
                                     val trackerVcsText: CharSequence)


  @JvmStatic
  fun installTrackerListener(viewer: DiffViewerBase, localRequest: LocalChangeListDiffRequest) {
    val trackerListener = MyTrackerListener(viewer)
    val lstmListener = MyLineStatusTrackerManagerListener(viewer, localRequest, trackerListener)

    LineStatusTrackerManager.getInstanceImpl(localRequest.project).addTrackerListener(lstmListener, viewer)

    val tracker = localRequest.lineStatusTracker as? PartialLocalLineStatusTracker
    if (tracker != null) tracker.addListener(trackerListener, viewer)
  }

  private class MyTrackerListener(private val viewer: DiffViewerBase)
    : PartialLocalLineStatusTracker.ListenerAdapter() {

    override fun onBecomingValid(tracker: PartialLocalLineStatusTracker) {
      viewer.scheduleRediff()
    }

    override fun onChangeListMarkerChange(tracker: PartialLocalLineStatusTracker) {
      viewer.scheduleRediff()
    }

    override fun onExcludedFromCommitChange(tracker: PartialLocalLineStatusTracker) {
      viewer.scheduleRediff()
    }
  }

  private class MyLineStatusTrackerManagerListener(private val viewer: DiffViewerBase,
                                                   private val localRequest: LocalChangeListDiffRequest,
                                                   private val trackerListener: PartialLocalLineStatusTracker.Listener)
    : LineStatusTrackerManager.ListenerAdapter() {

    override fun onTrackerAdded(tracker: LineStatusTracker<*>) {
      if (tracker is PartialLocalLineStatusTracker && tracker.virtualFile == localRequest.virtualFile) {
        tracker.addListener(trackerListener, viewer)
        viewer.scheduleRediff()
      }
    }
  }
}
