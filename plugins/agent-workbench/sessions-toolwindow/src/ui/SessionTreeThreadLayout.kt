// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.render.RenderingHelper
import com.intellij.ui.tree.ui.PlainSelectionTree
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.FontMetrics
import javax.swing.JTree

private const val SESSION_TREE_ACTION_SLOT_SIZE = 18
private const val SESSION_TREE_ACTION_GAP = 4
private const val SESSION_TREE_ACTION_RIGHT_GAP = 4
private const val SESSION_TREE_THREAD_META_LEFT_GAP = 8
private const val SESSION_TREE_THREAD_META_RIGHT_GAP = 2
private const val SESSION_TREE_THREAD_STATUS_TIME_GAP = 8
private const val SESSION_TREE_THREAD_SELECTION_HORIZONTAL_INSET = 12

internal data class SessionTreeViewportLayout(
  @JvmField val x: Int,
  @JvmField val width: Int,
  @JvmField val rightMargin: Int,
  @JvmField val selectionRightInset: Int,
) {
  val rightBoundary: Int
    get() = x + width - rightMargin - selectionRightInset
}

internal data class SessionTreeThreadHorizontalLayout(
  @JvmField val reserveWidth: Int,
  @JvmField val titleMaxWidth: Int,
  @JvmField val statusX: Int,
  @JvmField val statusRightBoundary: Int,
  @JvmField val timeX: Int,
  @JvmField val timeRightBoundary: Int,
)

internal data class SessionTreeThreadTrailingPaint(
  @JvmField val reserveWidth: Int,
  @JvmField val statusLabel: @NlsSafe String?,
  @JvmField val statusX: Int,
  @JvmField val statusRightBoundary: Int,
  @JvmField val statusTextWidth: Int,
  @JvmField val statusColumnWidth: Int,
  @JvmField val statusColor: Color?,
  @JvmField val timeLabel: @NlsSafe String,
  @JvmField val timeX: Int,
  @JvmField val timeRightBoundary: Int,
  @JvmField val timeTextWidth: Int,
  @JvmField val timeColumnWidth: Int,
  @JvmField val actionRightPadding: Int,
  @JvmField val selectionRightInset: Int,
)

internal fun sessionTreeActionSlotSize(): Int = JBUI.scale(SESSION_TREE_ACTION_SLOT_SIZE)

internal fun sessionTreeActionGap(): Int = JBUI.scale(SESSION_TREE_ACTION_GAP)

internal fun sessionTreeActionRightGap(): Int = JBUI.scale(SESSION_TREE_ACTION_RIGHT_GAP)

internal fun sessionTreeRowActionRightPadding(actionSlots: Int): Int {
  if (actionSlots == 0) return 0
  val slot = sessionTreeActionSlotSize()
  val gap = sessionTreeActionGap()
  val rightGap = sessionTreeActionRightGap()
  return rightGap + (actionSlots * slot) + (if (actionSlots > 1) (actionSlots - 1) * gap else 0)
}

internal fun sessionTreeRowActionsRightBoundary(
  helperX: Int = 0,
  helperWidth: Int,
  helperRightMargin: Int,
  rightGap: Int,
  selectionRightInset: Int,
): Int {
  return helperX + helperWidth - helperRightMargin - selectionRightInset - rightGap
}

internal fun configureSessionTreeRenderingProperties(tree: Tree) {
  tree.setExpandableItemsEnabled(false)
  tree.putClientProperty(AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
  tree.putClientProperty(RenderingHelper.SHRINK_LONG_RENDERER, true)
  tree.putClientProperty(RenderingHelper.SHRINK_LONG_SELECTION, true)
}

internal fun resolveSessionTreeViewportLayout(
  helperX: Int,
  helperWidth: Int,
  helperRightMargin: Int,
  selectionRightInset: Int,
): SessionTreeViewportLayout {
  return SessionTreeViewportLayout(
    x = helperX,
    width = helperWidth,
    rightMargin = helperRightMargin,
    selectionRightInset = selectionRightInset,
  )
}

internal fun resolveSessionTreeViewportLayout(tree: JTree): SessionTreeViewportLayout {
  val helper = RenderingHelper(tree)
  return resolveSessionTreeViewportLayout(
    helperX = helper.x,
    helperWidth = helper.width,
    helperRightMargin = helper.rightMargin,
    selectionRightInset = sessionTreeThreadSelectionRightInset(tree),
  )
}

internal fun isSessionTreeRowClipped(
  pathBoundsX: Int,
  pathBoundsWidth: Int,
  helperX: Int,
  helperWidth: Int,
  helperRightMargin: Int,
  selectionRightInset: Int,
): Boolean {
  val viewportLayout = resolveSessionTreeViewportLayout(
    helperX = helperX,
    helperWidth = helperWidth,
    helperRightMargin = helperRightMargin,
    selectionRightInset = selectionRightInset,
  )
  val visibleRightBoundary = viewportLayout.rightBoundary
  val rowRightBoundary = pathBoundsX + pathBoundsWidth
  return pathBoundsX < helperX || rowRightBoundary > visibleRightBoundary
}

internal fun resolveSessionTreeThreadTooltipWidth(
  helperWidth: Int,
  helperRightMargin: Int,
  selectionRightInset: Int,
): Int? {
  val availableWidth = helperWidth - helperRightMargin - selectionRightInset - JBUI.scale(16)
  return availableWidth.takeIf { it > 0 }
}

internal fun computeSessionTreeThreadHorizontalLayout(
  contentWidth: Int,
  actionRightPadding: Int,
  selectionRightInset: Int,
  timeTextWidth: Int,
  timeColumnWidth: Int,
  statusTextWidth: Int = 0,
  statusColumnWidth: Int = 0,
): SessionTreeThreadHorizontalLayout {
  val rightGap = JBUI.scale(SESSION_TREE_THREAD_META_RIGHT_GAP)
  val leftGap = JBUI.scale(SESSION_TREE_THREAD_META_LEFT_GAP)
  val statusTimeGap = if (statusColumnWidth > 0) JBUI.scale(SESSION_TREE_THREAD_STATUS_TIME_GAP) else 0
  val reserveWidth = actionRightPadding + selectionRightInset + rightGap + leftGap + timeColumnWidth + statusTimeGap + statusColumnWidth
  val titleMaxWidth = (contentWidth - reserveWidth).coerceAtLeast(0)
  val timeRightBoundary = (contentWidth - selectionRightInset - actionRightPadding - rightGap).coerceAtLeast(0)
  val timeX = (timeRightBoundary - timeTextWidth).coerceAtLeast(0)
  val statusRightBoundary = (timeX - statusTimeGap).coerceAtLeast(0)
  val statusX = (statusRightBoundary - statusTextWidth).coerceAtLeast(0)
  return SessionTreeThreadHorizontalLayout(
    reserveWidth = reserveWidth,
    titleMaxWidth = titleMaxWidth,
    statusX = statusX,
    statusRightBoundary = statusRightBoundary,
    timeX = timeX,
    timeRightBoundary = timeRightBoundary,
  )
}

internal fun isSessionTreeThreadTitleClipped(
  title: @NlsSafe String,
  fontMetrics: FontMetrics,
  titleMaxWidth: Int,
): Boolean {
  return fontMetrics.stringWidth(title) > titleMaxWidth
}

internal fun computeSessionTreeThreadTrailingPaint(
  tree: JTree,
  actionRightPadding: Int,
  timeLabel: @NlsSafe String?,
  statusLabel: @NlsSafe String? = null,
  statusColor: Color? = null,
  fontMetrics: FontMetrics,
  sharedTimeColumnWidth: Int,
  sharedStatusColumnWidth: Int = 0,
): SessionTreeThreadTrailingPaint? {
  if (timeLabel == null) return null
  val selectionRightInset = sessionTreeThreadSelectionRightInset(tree)
  val statusTextWidth = statusLabel?.let(fontMetrics::stringWidth) ?: 0
  val statusColumnWidth = if (statusLabel == null) 0 else maxOf(sharedStatusColumnWidth, statusTextWidth)
  val timeTextWidth = fontMetrics.stringWidth(timeLabel)
  val timeColumnWidth = maxOf(sharedTimeColumnWidth, timeTextWidth)
  val horizontalLayout = computeSessionTreeThreadHorizontalLayout(
    contentWidth = tree.width,
    actionRightPadding = actionRightPadding,
    selectionRightInset = selectionRightInset,
    timeTextWidth = timeTextWidth,
    timeColumnWidth = timeColumnWidth,
    statusTextWidth = statusTextWidth,
    statusColumnWidth = statusColumnWidth,
  )

  return SessionTreeThreadTrailingPaint(
    reserveWidth = horizontalLayout.reserveWidth,
    statusLabel = statusLabel,
    statusX = horizontalLayout.statusX,
    statusRightBoundary = horizontalLayout.statusRightBoundary,
    statusTextWidth = statusTextWidth,
    statusColumnWidth = statusColumnWidth,
    statusColor = statusColor,
    timeLabel = timeLabel,
    timeX = horizontalLayout.timeX,
    timeRightBoundary = horizontalLayout.timeRightBoundary,
    timeTextWidth = timeTextWidth,
    timeColumnWidth = timeColumnWidth,
    actionRightPadding = actionRightPadding,
    selectionRightInset = selectionRightInset,
  )
}

internal fun resolveSessionTreeThreadTimePaintX(
  preferredX: Int,
  rendererWidth: Int,
  timeTextWidth: Int,
  selectionRightInset: Int = 0,
): Int {
  val maxVisibleX = (
    rendererWidth -
    selectionRightInset -
    JBUI.scale(SESSION_TREE_THREAD_META_RIGHT_GAP) -
    timeTextWidth
  ).coerceAtLeast(0)
  return preferredX.coerceAtMost(maxVisibleX)
}

internal fun sessionTreeThreadSelectionRightInset(tree: JTree): Int {
  if (!ExperimentalUI.isNewUI()) return 0
  if (!Registry.`is`("ide.experimental.ui.tree.selection")) return 0
  if (tree is PlainSelectionTree) return 0
  return JBUI.scale(SESSION_TREE_THREAD_SELECTION_HORIZONTAL_INSET)
}
