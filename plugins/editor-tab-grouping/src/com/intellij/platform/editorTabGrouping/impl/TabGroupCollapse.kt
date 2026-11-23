// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editorTabGrouping.impl

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.SimpleColoredText
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tabs.TabInfo
import java.util.IdentityHashMap

/** The pinned state and the key of a tab group. */
internal typealias TabGroupKey = Pair<Boolean, String>

/**
 * One collapsed group. The [groupTab] is the member that stays visible and shows the group label.
 * The other [members] are hidden. The [members] keep the tab order of the expanded group.
 */
private class CollapsedTabGroup(val group: TabGroup, val members: MutableList<TabInfo>, var groupTab: TabInfo)

/** The steps of a file close in the editor window. The window needs each tab visible until it removes the tab of the file. */
private enum class FileClose {
  NONE,

  /** Each group is expanded, and no group collapses. */
  EXPANDED,

  /** The close needs the expanded groups no more. The next update collapses the groups again, and the user must not see it. */
  RESTORING,
}

/** The part of the tab component that [TabGroupCollapser] needs. */
internal interface TabGroupCollapseHost {
  /** True when the settings ask for collapsed groups. */
  val isCollapseEnabled: Boolean
  val isDragging: Boolean
  val snapshot: TabGroupingSnapshot

  /** The tab that the component reports as selected. Without a selection the component reports its first tab. */
  val selectedTab: TabInfo?

  /** True when the editor window has a current editor. The selection at startup sends no event. */
  val hasWindowSelection: Boolean
  val visibleTabs: List<TabInfo>

  /** Puts the tabs in the [order]. */
  fun applyOrder(order: List<TabInfo>)

  /**
   * Reports that the group of [members] collapses into the [groupTab] or expands from it.
   * The call comes before the change, so the host can read the layout that the user sees.
   * A change that the user must not see, such as the expand for a file close, sends no report.
   */
  fun groupChanging(groupTab: TabInfo, members: List<TabInfo>, collapsing: Boolean)

  /** Brings the snapshot, the tab order and the layout up to date after a change of the hidden tabs. */
  fun collapseChanged()
}

/**
 * Collapses each shown tab group that does not hold the selected tab.
 *
 * A collapsed group shows one member, the group tab, and hides the other members. The group tab is a real
 * editor tab with the label of the group, so the editor window and the group painter handle it as each other tab.
 * No tab is added, removed or created again.
 */
internal class TabGroupCollapser(private val host: TabGroupCollapseHost) {
  private val collapsedGroups = HashMap<TabGroupKey, CollapsedTabGroup>()

  /** The selection counter value of each tab that had the selection. A larger value is a later selection. */
  private val selectionOrder = IdentityHashMap<TabInfo, Long>()
  private var selectionCount = 0L
  private var fileClose = FileClose.NONE

  /** True while a change runs that the user must not see. Such a change sends no group report to the host. */
  private var silentChange = false

  /** True while one update changes the hidden state of many tabs. */
  var isUpdating: Boolean = false
    private set

  val hasCollapsedGroups: Boolean get() = collapsedGroups.isNotEmpty()

  fun groupTab(key: TabGroupKey): TabInfo? = collapsedGroups[key]?.groupTab

  fun isGroupTab(info: TabInfo): Boolean = collapsedGroups.values.any { it.groupTab === info }

  fun tabSelected(info: TabInfo) {
    selectionOrder[info] = ++selectionCount
    update()
  }

  fun tabRemoved(info: TabInfo) {
    selectionOrder.remove(info)
    if (fileClose == FileClose.EXPANDED) fileClose = FileClose.RESTORING
  }

  /** Expands each group and forgets the selection order. The component gets a new tab list after it. */
  fun reset() {
    expandAll()
    selectionOrder.clear()
  }

  /** Collapses each shown group that does not hold the selected tab, and expands each other group. */
  fun update() {
    if (isUpdating || fileClose == FileClose.EXPANDED || host.isDragging) return
    val silent = fileClose == FileClose.RESTORING
    fileClose = FileClose.NONE
    val enabled = host.isCollapseEnabled
    if (!enabled && collapsedGroups.isEmpty()) return
    val snapshot = host.snapshot
    val selected = knownSelectedTab()
    val selectedKey = selected?.let { info -> snapshot.groups[info]?.let { info.isPinned to it.key } }
    val wanted = if (enabled && selected != null) {
      snapshot.members.filter { (key, members) -> members.size > 1 && key != selectedKey }
    }
    else emptyMap()
    val upToDate = wanted.keys == collapsedGroups.keys &&
                   wanted.all { (key, members) -> collapsedGroups.getValue(key).holdsExactly(members) }
    if (upToDate) return
    changeState(silent) {
      // Each release runs before each hide, so a tab that moved to another collapsed group stays hidden.
      for (key in collapsedGroups.keys.toList()) {
        val members = wanted[key]
        if (members == null) expand(key) else releaseLeavers(collapsedGroups.getValue(key), members)
      }
      for ((key, members) in wanted) {
        val collapsed = collapsedGroups[key]
        if (collapsed == null) collapse(key, members) else hideMembers(collapsed, members)
      }
    }
  }

  /** Returns the selected tab, or null when no tab has the selection yet. */
  private fun knownSelectedTab(): TabInfo? {
    val selected = host.selectedTab ?: return null
    if (selected in selectionOrder) return selected
    if (!host.hasWindowSelection) return null
    selectionOrder[selected] = ++selectionCount
    return selected
  }

  /** Expands the collapsed group that holds [info]. The caller selects [info] after it. */
  fun expandGroupOf(info: TabInfo) {
    val key = collapsedGroups.entries.firstOrNull { info in it.value.members }?.key ?: return
    changeState(silent = false) { expand(key) }
  }

  /**
   * Expands each group before the editor window closes a file.
   *
   * The editor window selects the next tab on a close by index. It compares an index of the visible tabs
   * with an index of all tabs, and hidden tabs make them differ.
   */
  fun fileCloseStarted() {
    // Without a collapsed group the state still stops an update, so no group collapses around the tab that the close removes.
    fileClose = FileClose.EXPANDED
    expandAll()
  }

  /** Collapses the groups again when the close removed no tab of this component. */
  fun fileCloseFinished() {
    if (fileClose != FileClose.EXPANDED) return
    fileClose = FileClose.RESTORING
    update()
  }

  private fun expandAll() {
    if (collapsedGroups.isEmpty()) return
    changeState(silent = true) {
      for (key in collapsedGroups.keys.toList()) expand(key)
    }
  }

  /** Runs [change] and then lets the host bring the component up to date one time. A [silent] change sends no group report. */
  private fun changeState(silent: Boolean, change: () -> Unit) {
    isUpdating = true
    silentChange = silent
    try {
      change()
    }
    finally {
      isUpdating = false
      silentChange = false
    }
    host.collapseChanged()
  }

  private fun CollapsedTabGroup.holdsExactly(liveMembers: List<TabInfo>): Boolean =
    members.size == liveMembers.size && members.containsAll(liveMembers) && groupTab in members

  private fun collapse(key: TabGroupKey, members: List<TabInfo>) {
    val group = host.snapshot.groups[members.first()] ?: return
    val collapsed = CollapsedTabGroup(group, members.toMutableList(), lastUsedTab(members))
    collapsedGroups[key] = collapsed
    if (!silentChange) host.groupChanging(collapsed.groupTab, collapsed.members, collapsing = true)
    hideAllButGroupTab(collapsed)
  }

  /** Returns the member that had the selection last, or the first member when no member had it. */
  private fun lastUsedTab(members: List<TabInfo>): TabInfo = members.maxBy { selectionOrder[it] ?: 0L }

  private fun hideAllButGroupTab(collapsed: CollapsedTabGroup) {
    for (member in collapsed.members) member.isHidden = member !== collapsed.groupTab
  }

  /** Shows the tabs that left the group of [collapsed] again. */
  private fun releaseLeavers(collapsed: CollapsedTabGroup, liveMembers: List<TabInfo>) {
    val leavers = collapsed.members.filter { it !in liveMembers }
    collapsed.members.removeAll(leavers)
    for (leaver in leavers) {
      if (leaver === collapsed.groupTab) restoreLabel(leaver)
      leaver.isHidden = false
    }
  }

  /** Adds the new members to [collapsed] and hides each member but the group tab. */
  private fun hideMembers(collapsed: CollapsedTabGroup, liveMembers: List<TabInfo>) {
    for (member in liveMembers) {
      if (member !in collapsed.members) collapsed.members.add(member)
    }
    if (collapsed.groupTab !in collapsed.members) collapsed.groupTab = lastUsedTab(collapsed.members)
    hideAllButGroupTab(collapsed)
  }

  private fun expand(key: TabGroupKey) {
    val collapsed = collapsedGroups.remove(key) ?: return
    if (!silentChange) host.groupChanging(collapsed.groupTab, collapsed.members, collapsing = false)
    restoreLabel(collapsed.groupTab)
    val anchor = host.visibleTabs.indexOf(collapsed.groupTab)
    for (member in collapsed.members) member.isHidden = false
    if (anchor >= 0) restoreMemberOrder(collapsed.members, anchor)
  }

  /**
   * Puts the [members] in their order at [anchor], the index that the group tab had among the visible tabs.
   * A tab that becomes visible again returns to its saved index, which is stale.
   */
  private fun restoreMemberOrder(members: List<TabInfo>, anchor: Int) {
    val visibleTabs = host.visibleTabs
    val visibleMembers = members.filter { it in visibleTabs }
    val others = visibleTabs.filter { it !in visibleMembers }
    host.applyOrder(others.take(anchor) + visibleMembers + others.drop(anchor))
  }

  /**
   * Shows the group name and the member count on each group tab, without the icon and the close button of its file.
   * Only the label changes. The text, the icon and the actions of the tab stay as they are.
   */
  fun updateLabels() {
    if (collapsedGroups.isEmpty()) return
    val names = collapsedGroupNames(collapsedGroups.values.map { it.group }, shownGroups())
    for (collapsed in collapsedGroups.values) {
      val text = collapsedTabLabel(names.getValue(collapsed.group.key), collapsed.members.size)
      val label = collapsed.groupTab.tabLabel ?: continue
      label.setText(SimpleColoredText(text, SimpleTextAttributes.REGULAR_ATTRIBUTES))
      label.setIcon(null)
      label.setTabActions(null)
    }
  }

  /** The platform wrote the text, the icon or the actions of [info] to its label. A group tab gets the group label back. */
  fun labelOverwritten(info: TabInfo) {
    if (isGroupTab(info)) updateLabels()
  }

  private fun restoreLabel(info: TabInfo) {
    val label = info.tabLabel ?: return
    label.setText(info.coloredText)
    label.setIcon(info.icon)
    label.setTabActions(info.tabLabelActions)
  }

  /** Returns the groups that the current tab order shows, one entry per key. */
  private fun shownGroups(): List<TabGroup> {
    val snapshot = host.snapshot
    val result = LinkedHashMap<String, TabGroup>()
    for ((key, members) in snapshot.members) {
      if (members.size > 1) snapshot.groups[members.first()]?.let { result.putIfAbsent(key.second, it) }
    }
    return result.values.toList()
  }
}

/** Returns the label of a group tab: the display name and the member count. */
internal fun collapsedTabLabel(displayName: String, memberCount: Int): @NlsContexts.Label String =
  EditorTabGroupingBundle.message("tab.collapsed.label", displayName, memberCount)

/**
 * Shortens a disambiguated path name to the differing directory and the group name.
 * "mod1/com/src" becomes "mod1/…/src". A name of two parts or fewer keeps its form.
 */
internal fun collapsedTabDisplayName(displayName: String, groupName: String): String {
  val parts = displayName.split('/')
  if (parts.size < 3 || parts.last() != groupName || parts.first().isEmpty()) return displayName
  return "${parts.first()}/…/$groupName"
}

/**
 * Returns the display name of each of the [collapsed] groups by group key.
 *
 * A name that collides with a name of the [shownGroups] gets the first differing directory.
 * When two short names are equal, the two groups keep the full disambiguated name.
 */
internal fun collapsedGroupNames(collapsed: Collection<TabGroup>, shownGroups: List<TabGroup>): Map<String, String> {
  val fullNames = collapsed.associate { it.key to disambiguateVisibleGroupName(it, shownGroups) }
  val shortNames = collapsed.associate { it.key to collapsedTabDisplayName(fullNames.getValue(it.key), it.name) }
  val shortNameCounts = shortNames.values.groupingBy { it }.eachCount()
  return shortNames.mapValues { (key, shortName) -> if (shortNameCounts.getValue(shortName) > 1) fullNames.getValue(key) else shortName }
}

/** Returns the enabled tab nearest to [removedIndex]: the previous tabs first, then the next tabs. */
internal fun findSelectionNeighbor(tabs: List<TabInfo>, removedIndex: Int): TabInfo? {
  for (index in removedIndex - 1 downTo 0) {
    if (tabs[index].isEnabled) return tabs[index]
  }
  for (index in (removedIndex + 1) until tabs.size) {
    if (tabs[index].isEnabled) return tabs[index]
  }
  return null
}
