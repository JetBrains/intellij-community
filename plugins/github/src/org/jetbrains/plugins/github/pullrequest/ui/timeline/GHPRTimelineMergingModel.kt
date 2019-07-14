// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

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
    var lastListIdx = max(0, list.lastIndex)
    var lastEvent: GHPRTimelineEvent? = list.lastOrNull() as? GHPRTimelineEvent
    if (lastEvent != null) {
      list.removeAt(lastListIdx)
      fireIntervalRemoved(this, lastListIdx, lastListIdx)
      lastListIdx--
    }

    for (item in items) {
      if (item !is GHPRTimelineEvent) {
        if (lastEvent != null) {
          if (!isCollapsedMerge(lastEvent)) list.add(lastEvent)
          lastEvent = null
        }
        list.add(item)
      }
      else {
        if (lastEvent != null) {
          val merged = mergeIfPossible(lastEvent, item)
          if (merged != null) {
            lastEvent = merged
          }
          else {
            if (!isCollapsedMerge(lastEvent)) list.add(lastEvent)
            lastEvent = item
          }
        }
        else {
          lastEvent = item
        }
      }
    }
    if (lastEvent != null && !isCollapsedMerge(lastEvent)) list.add(lastEvent)
    fireIntervalAdded(this, max(0, lastListIdx), list.lastIndex)
  }

  fun removeAll() {
    val lastIdx = max(0, size - 1)
    list.clear()
    if (lastIdx > 0) fireIntervalRemoved(this, 0, lastIdx)
  }

  companion object {
    private const val MERGE_THRESHOLD_MS = DateFormatUtil.MINUTE * 2

    private fun mergeIfPossible(existing: GHPRTimelineEvent, new: GHPRTimelineEvent): GHPRTimelineEvent? {
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

    private fun isCollapsedMerge(event: GHPRTimelineEvent) = event is GHPRTimelineMergedEvents<*> && !event.hasAnyChanges()
  }
}
