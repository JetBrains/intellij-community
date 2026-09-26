// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import javax.swing.AbstractListModel
import kotlin.math.max
import kotlin.time.Duration.Companion.minutes

internal class GHPRTimelineMergingModel : AbstractListModel<GHPRTimelineItem>() {
  private val unmergedList = mutableListOf<GHPRTimelineItem>()
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
      unmergedList.add(item)
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

  fun update(idx: Int, newItem: GHPRTimelineItem) {
    val originalItem = unmergedList.getOrNull(idx) ?: return
    val mergedIdx = list.indexOf(originalItem)
    if (mergedIdx >= 0) {
      unmergedList[idx] = newItem
      list[mergedIdx] = newItem
    }
    fireContentsChanged(this, mergedIdx, mergedIdx)
  }

  fun remove(idx: Int) {
    val originalItem = unmergedList.getOrNull(idx) ?: return
    val mergedIdx = list.indexOf(originalItem)
    if (mergedIdx >= 0) {
      unmergedList.removeAt(idx)
      list.removeAt(mergedIdx)
    }
    fireIntervalRemoved(this, mergedIdx, mergedIdx)
  }

  fun removeAll() {
    val lastIdx = max(0, size - 1)
    list.clear()
    if (lastIdx > 0) fireIntervalRemoved(this, 0, lastIdx)
  }

  companion object {
    private val MERGE_THRESHOLD_MS = 2.minutes.inWholeMilliseconds

    private fun mergeIfPossible(existing: GHPRTimelineItem?, new: GHPRTimelineItem?): GHPRTimelineItem? {
      val groupedCommits = tryGroupCommits(existing, new)
      if (groupedCommits != null) {
        return groupedCommits
      }

      if (existing !is GHPRTimelineEvent || new !is GHPRTimelineEvent) return null
      if (existing.actor != new.actor) return null
      if (new.createdAt.time - existing.createdAt.time > MERGE_THRESHOLD_MS) return null

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
      return null
    }

    private fun tryGroupCommits(existing: GHPRTimelineItem?, new: GHPRTimelineItem?): GHPRTimelineItem? {
      if (existing is GHPullRequestCommitShort && new is GHPullRequestCommitShort) {
        return GHPRTimelineGroupedCommits().apply {
          add(existing)
          add(new)
        }
      }

      if (existing is GHPRTimelineGroupedCommits && new is GHPullRequestCommitShort) {
        return existing.apply {
          add(new)
        }
      }

      if (existing is GHPullRequestCommitShort && new is GHPRTimelineGroupedCommits) {
        return GHPRTimelineGroupedCommits().apply {
          add(existing)
          for (item in new.items) {
            add(item)
          }
        }
      }

      if (existing is GHPRTimelineGroupedCommits && new is GHPRTimelineGroupedCommits) {
        return existing.apply {
          for (item in new.items) {
            add(item)
          }
        }
      }
      return null
    }

    private fun isCollapsedMerge(event: GHPRTimelineItem) = event is GHPRTimelineMergedEvents<*> && !event.hasAnyChanges()
  }
}
