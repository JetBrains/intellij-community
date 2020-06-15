// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.text.DateFormatUtil
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import javax.swing.AbstractListModel
import kotlin.math.max

class GHPRTimelineMergingModel : AbstractListModel<GHPRTimelineItem>() {
  private val list = mutableListOf<GHPRTimelineItem>()

  override fun getElementAt(index: Int): GHPRTimelineItem = list[index]

  override fun getSize(): Int = list.size

  fun add(items: List<GHPRTimelineItem>) {
    var lastListIdx = list.lastIndex
    var lastItem: GHPRTimelineItem? = list.lastOrNull()
    if (lastItem != null) {
      list.removeAt(lastListIdx)
      fireIntervalRemoved(this, lastListIdx, lastListIdx)
      lastListIdx--
    }

    var added = false
    val hideUnknown = ApplicationManager.getApplication().let { !it.isInternal && !it.isEAP }
    for (item in items) {
      if (item is GHPRTimelineItem.Unknown && (hideUnknown || item.__typename in GHPRTimelineItem.IGNORED_TYPES)) continue
      val merged = mergeIfPossible(lastItem, item)
      if (merged != null) {
        lastItem = merged
      }
      else {
        if (lastItem != null && !isCollapsedMerge(lastItem)) {
          list.add(lastItem)
          added = true
        }
        lastItem = item
      }
    }
    if (lastItem != null && !isCollapsedMerge(lastItem)) {
      list.add(lastItem)
      added = true
    }
    if (added) fireIntervalAdded(this, lastListIdx + 1, list.lastIndex)
  }

  fun removeAll() {
    val lastIdx = max(0, size - 1)
    list.clear()
    if (lastIdx > 0) fireIntervalRemoved(this, 0, lastIdx)
  }

  companion object {
    private const val MERGE_THRESHOLD_MS = DateFormatUtil.MINUTE * 2

    private fun mergeIfPossible(existing: GHPRTimelineItem?, new: GHPRTimelineItem?): GHPRTimelineEvent? {
      if (existing !is GHPRTimelineEvent || new !is GHPRTimelineEvent) return null

      if (existing.actor == new.actor && new.createdAt.time - existing.createdAt.time <= MERGE_THRESHOLD_MS) {
        if (existing is GHPRTimelineEvent.Simple && new is GHPRTimelineEvent.Simple) {
          if (existing is GHPRTimelineMergedSimpleEvents) {
            existing.add(new)
            return existing
          }
          else {
            return GHPRTimelineMergedSimpleEvents().apply {
              add(existing)
              add(new)
            }
          }
        }
        else if (existing is GHPRTimelineEvent.State && new is GHPRTimelineEvent.State) {
          if (existing is GHPRTimelineMergedStateEvents) {
            existing.add(new)
            return existing
          }
          else {
            return GHPRTimelineMergedStateEvents(existing).apply {
              add(new)
            }
          }
        }
      }
      return null
    }

    private fun isCollapsedMerge(event: GHPRTimelineItem) = event is GHPRTimelineMergedEvents<*> && !event.hasAnyChanges()
  }
}
