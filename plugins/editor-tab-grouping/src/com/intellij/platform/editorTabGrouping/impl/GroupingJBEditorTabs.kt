// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editorTabGrouping.impl

import com.intellij.ide.setToolTipText
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.impl.EditorTabs
import com.intellij.openapi.fileEditor.impl.EditorTabsFactory
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.createEditorTabListOptions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClientProperty
import com.intellij.ui.IslandsState
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.TabListOptions
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.UIManager
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly

private const val GROUP_LABEL_EXTRA_PX = 1

internal open class GroupingJBEditorTabs(
  project: Project?,
  parentDisposable: Disposable,
  coroutineScope: CoroutineScope,
  tabListOptions: TabListOptions,
  window: EditorWindow,
  private val isIslandsTheme: () -> Boolean = IslandsState::isEnabled,
  resolver: DirectoryGroupResolver = DirectoryGroupResolver(project),
) : EditorTabs(project, parentDisposable, coroutineScope, tabListOptions, window) {

  private val groupHeaderFontSize = JBUI.Fonts.miniFont().size.toFloat()

  private val settings = EditorTabGroupingSettings.getInstance()

  private val painter = TabGroupPainter(this)

  private var rearranging = false
  private var resettingDropTarget = false

  init {
    addListener(object : TabsListener {
      override fun tabsMoved() {
        rebuildSnapshot()
        if (!rearranging) rearrangeTabsByGroup(rebuildCache = false)
      }

      override fun tabRemoved(tabToRemove: TabInfo) {
        if (!rearranging && !resettingDropTarget) regroupAfterTabRemoved()
      }
    })
  }

  private var snapshot = TabGroupingSnapshot(emptyList()) { null }
  private val controller = TabGroupingController(project, coroutineScope, resolver) {
    rebuildSnapshot()
    rearrangeTabsByGroup(rebuildCache = false)
    revalidate()
    repaint()
  }
  private var dragCandidate: VirtualFile? = null

  @TestOnly
  internal suspend fun awaitGroupResolution() = controller.awaitResolution()

  internal fun onContentRootsChanged() = controller.invalidate()
  internal val retainedFileCount: Int get() = controller.retainedFileCount
  internal val dropRangeBuildCount: Int get() = snapshot.dropRangeBuildCount

  private fun invalidateGroupCache() {
    val files = tabs.asSequence().filter { it !== dropInfo }.mapNotNull { it.`object` as? VirtualFile }.toMutableSet()
    dragCandidate?.let(files::add)
    controller.refresh(files, settings.groupByDirectory)
    rebuildSnapshot()
  }

  private fun rebuildSnapshot() {
    snapshot = TabGroupingSnapshot(tabs.filter { it !== dropInfo }, controller::group)
    invalidateLayoutCaches()
    updateGroupTooltips()
    repaint()
  }

  /** Adds the group name and size above the base tooltip of each tab in a shown group. */
  private fun updateGroupTooltips() {
    val active = isGroupingActive()
    for (info in tabs) {
      if (info === dropInfo) continue
      val label = info.tabLabel ?: continue
      val group = if (active) snapshot.groups[info] else null
      if (group == null || !isVisibleGroup(info.isPinned, group.key)) {
        label.setToolTipText(info.tooltipHtmlText)
        continue
      }
      val header = EditorTabGroupingBundle.message("tab.tooltip.group", group.name)
      val builder = HtmlBuilder().append(HtmlChunk.text(header))
      info.tooltipHtmlText?.let { builder.append(HtmlChunk.br()).append(it) }
      label.setToolTipText(builder.toFragment())
    }
  }

  internal fun logicalGroupSizes(): Map<Pair<Boolean, String>, Int> = snapshot.sizes

  internal fun visibleGroupTabs(): List<TabInfo> = getVisibleInfos().filter { it !== dropInfo }

  fun groupFor(info: TabInfo): TabGroup? = snapshot.groups[info]

  private fun memoizedGroup(file: VirtualFile): TabGroup? = controller.group(file)

  private fun getGroupedInsertionIndex(file: VirtualFile, currentTabIndex: Int, isPinned: Boolean): Int? {
    if (!isGroupingActive()) return null
    val visibleInfos = getVisibleInfos()
    val group = memoizedGroup(file) ?: return null
    val groupIndices = visibleInfos.indices.filter { i ->
      val info = visibleInfos[i]
      info.isPinned == isPinned && groupFor(info)?.key == group.key
    }
    if (groupIndices.isEmpty()) return visibleInfos.size
    return when {
      UISettings.getInstance().openTabsAtTheEnd || currentTabIndex < 0 -> groupIndices.last() + 1
      currentTabIndex < groupIndices.first() -> groupIndices.first()
      currentTabIndex > groupIndices.last() -> groupIndices.last() + 1
      else -> currentTabIndex + 1
    }
  }

  override fun addTab(info: TabInfo, index: Int): TabInfo {
    val result = super.addTab(info, index)
    invalidateGroupCache()
    moveToGroupEnd(info)
    return result
  }

  private fun regroupAfterTabRemoved() {
    invalidateGroupCache()
    rearrangeTabsByGroup(rebuildCache = false)
  }

  override fun addTabSilently(info: TabInfo, index: Int): TabInfo {
    val result = super.addTabSilently(info, index)
    invalidateGroupCache()
    moveToGroupEnd(info)
    return result
  }

  override fun setTabsFromWindow(tabs: List<TabInfo>) {
    super.setTabsFromWindow(tabs)
    invalidateGroupCache()
    rearrangeTabsByGroup(rebuildCache = false)
  }

  override fun getPreferredInsertionIndex(file: VirtualFile, currentTabIndex: Int, isPinned: Boolean): Int? =
    getGroupedInsertionIndex(file, currentTabIndex, isPinned)

  override fun propertyChange(event: java.beans.PropertyChangeEvent) {
    super.propertyChange(event)
    if (event.propertyName == TabInfo.HIDDEN) rebuildSnapshot()
    else if (event.propertyName == TabInfo.TEXT) updateGroupTooltips()
  }

  override fun tabsChanged() {
    rearrangeTabsByGroup()
  }

  override fun prepareDropPreview(dropInfo: TabInfo, file: VirtualFile, isPinned: Boolean) {
    dropInfo.setObject(file)
    ClientProperty.put(dropInfo.component, PINNED, if (isPinned) true else null)
  }

  override fun additionalHeaderHeight(): Int = getGroupContainerHeaderHeight()

  override fun additionalTabLabelHeight(info: TabInfo): Int = getGroupTabLabelHeight(info)

  private fun moveToGroupEnd(info: TabInfo) {
    if (!isGroupingActive() || controller.isDragging) return
    val file = info.`object` as? VirtualFile
    if (file != null && !controller.isResolved(file)) return
    if (UISettings.getInstance().sortTabsAlphabetically) {
      rearrangeTabsByGroup(rebuildCache = false)
      return
    }
    val group = groupFor(info)
    if (group == null) {
      moveUngroupedToEnd(info)
      return
    }
    val groupSize = visibleGroupSize(info.isPinned, group.key)
    if (groupSize == 1) {
      moveUngroupedToEnd(info)
      return
    }
    if (groupSize == 2) {
      rearrangeTabsByGroup(rebuildCache = false)
      return
    }
    val currentIdx = (0 until tabCount).indexOfFirst { getTabAt(it) == info }
    if (currentIdx < 0) return
    val allowedPositions = computeGroupedDropPositions(currentTabGroups(), info.isPinned, group.key, currentIdx)
    if (currentIdx in allowedPositions) return
    val range = groupIndexRange(info.isPinned, group.key, excluding = info)
    if (range.isEmpty()) {
      // Defensive fallback for stale cache states: preserve grouping by running the full sorter.
      rearrangeTabsByGroup(rebuildCache = false)
      return
    }
    // Adjust for removal shift: reorderTab removes the tab first, then inserts.
    // If currentIdx < range.last: removal shifts range.last down by 1, so insert at range.last.
    // If currentIdx > range.last: no shift of earlier tabs, so insert at range.last + 1.
    val insertIdx = if (currentIdx < range.last) range.last else range.last + 1
    if (currentIdx == insertIdx) return
    rearranging = true
    try {
      moveTab(info, insertIdx)
    }
    finally {
      rearranging = false
    }
  }

  private fun moveUngroupedToEnd(info: TabInfo) {
    val currentIdx = (0 until tabCount).indexOfFirst { getTabAt(it) == info }
    if (currentIdx < 0) return
    val insertIdx = computeUngroupedInsertIndex(currentTabGroups(), info.isPinned, currentIdx) ?: return
    if (currentIdx == insertIdx) return
    rearranging = true
    try {
      moveTab(info, insertIdx)
    }
    finally {
      rearranging = false
    }
  }

  /** Moves [info] to [index] and drops the tab-order cache, which the move makes wrong. */
  private fun moveTab(info: TabInfo, index: Int) {
    reorderTab(info, index)
    rebuildSnapshot()
    tabMoved()
  }

  /** Test hook. It runs each time a rearrange moves a tab. */
  protected open fun tabMoved() {}

  // Suppress the base class's alphabetical sort-as-view-transform when grouping is active.
  // We apply the sort ourselves inside clusterTabsByGroup so that groups stay contiguous.
  override fun isAlphabeticalMode(): Boolean {
    return !isGroupingActive() && super.isAlphabeticalMode()
  }

  // Font metrics remain valid until layout or settings change.
  private var cachedGroupHeaderFontHeight = -1

  internal fun getGroupHeaderHeight(): Int {
    if (!isGroupingActive() || !effectiveShowGroupNames()) return 0
    if (cachedGroupHeaderFontHeight < 0) {
      cachedGroupHeaderFontHeight = computeGroupHeaderFontHeight()
    }
    return cachedGroupHeaderFontHeight
  }

  /** Derives the group label font and measures it. This is the expensive part of the height. */
  protected open fun computeGroupHeaderFontHeight(): Int {
    val baseFont = font ?: UIManager.getFont("Label.font") ?: return 0
    val fm = getFontMetrics(baseFont.deriveFont(groupHeaderFontSize))
    return fm.height + JBUIScale.scale(GROUP_LABEL_EXTRA_PX)
  }

  private fun getGroupContainerHeaderHeight(): Int {
    val labelHeight = getGroupHeaderHeight()
    if (labelHeight == 0) return 0
    val tabTopInset = JBUI.scale(getTabLabelInsets().unscaled.top)
    return (labelHeight + tabTopInset).coerceAtLeast(0)
  }

  internal fun effectiveGroupLabelStyle(): GroupLabelStyle =
    if (isIslandsTheme()) settings.groupLabelStyle else GroupLabelStyle.RAIL

  internal fun effectiveShowGroupNames(): Boolean =
    isIslandsTheme() && settings.showGroupNames

  // Returns the group label height for the tab that displays the group strip.
  // Horizontal: every tab carries the strip (shared across the row).
  // Vertical: only the first (topmost in list order) tab of each group gets the strip;
  // subsequent group members have no extra top space, avoiding wasteful blank rows.
  private fun getGroupTabLabelHeight(info: TabInfo): Int {
    val glh = getGroupHeaderHeight()
    if (glh <= 0) return 0
    if (isHorizontalTabs) return glh
    return if (info in groupStripOwners()) glh else 0
  }

  // The first visible tab of each visible group. Built one time per layout, so the vertical
  // label height stays constant-time per tab.
  private var cachedGroupStripOwners: Set<TabInfo>? = null

  private fun groupStripOwners(): Set<TabInfo> {
    cachedGroupStripOwners?.let { return it }
    val owners = Collections.newSetFromMap(IdentityHashMap<TabInfo, Boolean>())
    val seen = HashSet<Pair<Boolean, String>>()
    for (info in getVisibleInfos()) {
      val group = groupFor(info) ?: continue
      if (!isVisibleGroup(info.isPinned, group.key)) continue
      if (seen.add(info.isPinned to group.key)) owners.add(info)
    }
    cachedGroupStripOwners = owners
    return owners
  }

  /** Drops every value that only one layout pass may use. */
  private fun invalidateLayoutCaches() {
    painter.invalidate()
    cachedGroupStripOwners = null
    cachedGroupHeaderFontHeight = -1
  }

  override fun doLayout() {
    invalidateLayoutCaches()
    super.doLayout()
  }

  internal open fun outlineDataBuilt() {}
  internal open fun groupNamesBuilt() {}
  internal open fun groupLabelPainted(areaX: Int, areaWidth: Int, drawnWidth: Int) {}

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    painter.paintBackground(g)
  }

  override fun paint(g: Graphics) {
    super.paint(g)
    painter.paintForeground(g)
  }

  internal fun rearrangeTabsByGroup(rebuildCache: Boolean = true) {
    if (!settings.groupByDirectory || controller.isDragging) return
    if (rebuildCache) invalidateGroupCache()
    if (!isGroupingActive()) return
    rearranging = true
    try {
      val (pinned, unpinned) = (0 until tabCount).map { getTabAt(it) }.partition { it.isPinned }

      fun List<TabInfo>.clusterByGroup(): List<TabInfo> =
        clusterTabsByGroup(this, ::groupFor, UISettings.getInstance().sortTabsAlphabetically)

      val reordered = pinned.clusterByGroup() + unpinned.clusterByGroup()
      applyTabOrder(reordered)
    }
    finally {
      rearranging = false
    }
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    super.uiSettingsChanged(uiSettings)
    invalidateGroupCache()
    rearrangeTabsByGroup(rebuildCache = false)
    revalidate()
    repaint()
  }

  override fun isDropIndexAllowed(dropIndex: Int, draggedInfo: TabInfo): Boolean {
    if (!isGroupingActive()) return true
    val groupKey = (groupFor(draggedInfo) ?: groupForDirectly(draggedInfo))?.key
    val index = if (dropIndex < 0) snapshot.visibleTabs.size else dropIndex
    return index in snapshot.dropRange(draggedInfo, groupKey)
  }

  override fun canReallocateTo(source: TabInfo?, target: TabInfo?): Boolean {
    if (!isGroupingActive()) return super.canReallocateTo(source, target)
    if (source == target || source == null || target == null) return false
    val sourceIndex = snapshot.visibleIndices[source] ?: return false
    val targetIndex = snapshot.visibleIndices[target] ?: return false
    if (sourceIndex == targetIndex) return false
    val groupKey = groupFor(source)?.key
    return targetIndex in snapshot.dropRange(source, groupKey)
  }

  override fun reallocate(source: TabInfo?, target: TabInfo?) {
    if (!canReallocateTo(source, target)) return
    val sourceInfo = source ?: return
    val targetInfo = target ?: return
    if (!isGroupingActive()) {
      super.reallocate(source, target)
    }
    else {
      reorderTab(sourceInfo, snapshot.visibleIndices.getValue(targetInfo))
    }
    rebuildSnapshot()
  }

  override fun onDragStateChanged(active: Boolean) {
    controller.dragStateChanged(active)
    if (!active) {
      dragCandidate = null
      invalidateGroupCache()
      rearrangeTabsByGroup(rebuildCache = false)
    }
  }

  override fun startDropOver(tabInfo: TabInfo, point: com.intellij.ui.awt.RelativePoint): java.awt.Image {
    controller.previewStateChanged(true)
    dragCandidate = tabInfo.`object` as? VirtualFile
    invalidateGroupCache()
    return super.startDropOver(tabInfo, point)
  }

  override fun resetDropOver(tabInfo: TabInfo) {
    resettingDropTarget = true
    try {
      super.resetDropOver(tabInfo)
    }
    finally {
      resettingDropTarget = false
      dragCandidate = null
      controller.previewStateChanged(false)
      invalidateGroupCache()
      repaint()
    }
  }

  // Resolves the group of a tab that the group cache does not hold, such as a drag source from
  // another window. The memo keeps a ReadAction off every mouse-move event during DnD.
  private fun groupForDirectly(info: TabInfo): TabGroup? =
    (info.`object` as? VirtualFile)?.let { memoizedGroup(it) }

  private fun groupIndexRange(isPinned: Boolean, groupKey: String, excluding: TabInfo? = null): IntRange {
    val tabs = currentTabGroups()
    val excludeIdx = (0 until tabCount).firstOrNull { getTabAt(it) == excluding } ?: -1
    return computeContiguousGroupRange(tabs, isPinned, groupKey, excludeIdx)
  }

  private fun visibleGroupSize(isPinned: Boolean, groupKey: String): Int =
    snapshot.sizes[isPinned to groupKey] ?: 0


  private fun applyTabOrder(reordered: List<TabInfo>) {
    val currentOrder = getVisibleInfos().filter { it !== dropInfo }
    // The order is already correct on most events, so this skips every move.
    if (currentOrder == reordered.filterNot { it.isHidden }) return
    val positions = IdentityHashMap<TabInfo, Int>(reordered.size)
    reordered.forEachIndexed { index, info -> positions[info] = index }
    sortTabs(compareBy { positions.getValue(it) })
    rebuildSnapshot()
    tabMoved()
  }

  private fun currentTabGroups(): List<Pair<Boolean, String?>> {
    return snapshot.tabGroups
  }

  private fun isVisibleGroup(isPinned: Boolean, groupKey: String): Boolean = visibleGroupSize(isPinned, groupKey) > 1

  internal fun isGroupingActive(excluding: TabInfo? = null): Boolean {
    return snapshot.isActive(settings.groupByDirectory, excluding)
  }
}

internal class GroupingEditorTabsFactory : EditorTabsFactory {
  override fun create(coroutineScope: CoroutineScope, parentDisposable: Disposable, window: EditorWindow): EditorTabs =
    GroupingJBEditorTabs(
      project = window.manager.project,
      parentDisposable = parentDisposable,
      coroutineScope = coroutineScope,
      tabListOptions = createEditorTabListOptions(),
      window = window,
    )
}
