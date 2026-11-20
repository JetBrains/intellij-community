// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editorTabGrouping.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tabs.TabInfo
import java.util.IdentityHashMap

/** Captures logical membership and visible indices for one tab order. */
internal class TabGroupingSnapshot(
  val tabs: List<TabInfo>,
  resolve: (VirtualFile) -> TabGroup?,
) {
  val groups: Map<TabInfo, TabGroup?> = tabs.associateWith { (it.`object` as? VirtualFile)?.let(resolve) }
  val sizes: Map<Pair<Boolean, String>, Int> = tabs.mapNotNull { info -> groups[info]?.let { info.isPinned to it.key } }
    .groupingBy { it }.eachCount()
  /** The tabs of each group by (isPinned, groupKey), in tab order. */
  val members: Map<Pair<Boolean, String>, List<TabInfo>> by lazy(LazyThreadSafetyMode.NONE) {
    val result = LinkedHashMap<Pair<Boolean, String>, MutableList<TabInfo>>()
    for (info in tabs) {
      val group = groups[info] ?: continue
      result.getOrPut(info.isPinned to group.key) { ArrayList() }.add(info)
    }
    result
  }
  val visibleGroupCount: Int = sizes.values.count { it > 1 }
  val largestGroupSize: Int = sizes.values.maxOrNull() ?: 0
  val tabGroups: List<Pair<Boolean, String?>> = tabs.map { it.isPinned to groups[it]?.key }
  val visibleTabs: List<TabInfo> = tabs.filterNot { it.isHidden }
  val visibleIndices: Map<TabInfo, Int> = IdentityHashMap<TabInfo, Int>().apply {
    visibleTabs.forEachIndexed { index, info -> put(info, index) }
  }
  private val excludedSizes = tabs.associateWith { sizes[it.isPinned to groups[it]?.key] ?: 0 }
  private val byFile = tabs.associateBy { it.`object` }
  private var lastSource: TabInfo? = null
  private var lastPinned = false
  private var lastGroupKey: String? = null
  private var lastRange = IntRange.EMPTY
  internal var dropRangeBuildCount: Int = 0
    private set

  fun isActive(configured: Boolean, excluding: TabInfo? = null): Boolean {
    val excluded = excluding?.takeIf { !it.isHidden && groups.containsKey(it) }
    val size = if (excluded == null) null else excludedSizes[excluded]
    return isTabGroupingActive(configured, visibleGroupCount, tabs.size, largestGroupSize, size)
  }

  fun sourceIndex(source: TabInfo): Int {
    visibleIndices[source]?.let { return it }
    val file = source.`object` as? VirtualFile ?: return -1
    return visibleIndices[byFile[file]] ?: -1
  }

  fun dropRange(source: TabInfo, groupKey: String?): IntRange {
    if (lastSource === source && lastPinned == source.isPinned && lastGroupKey == groupKey) return lastRange
    lastSource = source
    lastPinned = source.isPinned
    lastGroupKey = groupKey
    dropRangeBuildCount++
    val sourceIndex = sourceIndex(source)
    val visibleGroups = visibleTabs.map { it.isPinned to groups[it]?.key }
    val groupSize = sizes[source.isPinned to groupKey] ?: 0
    lastRange = if (groupKey != null && groupSize > 1) {
      val range = computeContiguousGroupRange(visibleGroups, source.isPinned, groupKey, sourceIndex, reindexed = true)
      if (range.isEmpty()) computeUngroupedDropPositions(visibleGroups, source.isPinned, sourceIndex)
      else range.first..range.last + 1
    }
    else computeUngroupedDropPositions(visibleGroups, source.isPinned, sourceIndex)
    return lastRange
  }
}
