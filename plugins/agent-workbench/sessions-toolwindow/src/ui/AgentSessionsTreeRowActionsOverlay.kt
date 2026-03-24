// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.withYoloModeBadge
import com.intellij.agent.workbench.sessions.toolwindow.tree.NewSessionRowActions
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.resolveNewSessionRowActions
import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.Icon

private const val SESSION_TREE_ACTION_ICON_SIZE = 14

internal enum class RowActionKind {
  QuickCreate,
  ShowPopup,
}

internal data class SessionTreeRowActionPresentation(
  @JvmField val showLoadingAction: Boolean,
  @JvmField val quickIcon: Icon?,
  @JvmField val showQuickAction: Boolean,
  @JvmField val showPopupAction: Boolean,
  @JvmField val hoveredKind: RowActionKind?,
) {
  val actionSlots: Int
    get() =
      (if (showLoadingAction) 1 else 0) +
      (if (showQuickAction) 1 else 0) +
      (if (showPopupAction) 1 else 0)
}

private data class RowActionRects(
  @JvmField val loadingRect: Rectangle?,
  @JvmField val quickRect: Rectangle?,
  @JvmField val popupRect: Rectangle?,
)

private data class RowActionHit(
  @JvmField val row: Int,
  @JvmField val nodeId: SessionTreeId,
  @JvmField val node: SessionTreeNode,
  @JvmField val kind: RowActionKind,
  @JvmField val actions: NewSessionRowActions,
  @JvmField val rects: RowActionRects,
)

internal class AgentSessionsTreeRowActionsOverlay(
  private val tree: Tree,
  private val nodeResolver: (SessionTreeId) -> SessionTreeNode?,
  private val lastUsedProvider: () -> AgentSessionProvider?,
  private val lastUsedLaunchMode: () -> AgentSessionLaunchMode?,
  private val onQuickCreate: (path: String, provider: AgentSessionProvider, mode: AgentSessionLaunchMode) -> Unit,
  private val onShowPopup: (nodeId: SessionTreeId, node: SessionTreeNode, anchorRect: Rectangle, row: Int) -> Unit,
) {
  private var hoveredRowAction: RowActionHit? = null
  private var popupPinnedRow: Int? = null

  fun rowActionPresentation(
    row: Int,
    treeNode: SessionTreeNode,
    selected: Boolean,
  ): SessionTreeRowActionPresentation? {
    val rowActions = resolveNewSessionRowActions(treeNode, lastUsedProvider(), lastUsedLaunchMode()) ?: return null
    val isHovered = TreeHoverListener.getHoveredRow(tree) == row
    val isPinned = popupPinnedRow == row
    val showInteractiveActions = selected || isHovered || isPinned
    val showLoadingAction = when (treeNode) {
      is SessionTreeNode.Project -> treeNode.project.isLoading
      is SessionTreeNode.Worktree -> treeNode.worktree.isLoading
      else -> false
    }
    if (!showInteractiveActions && !showLoadingAction) return null

    val quickIcon = rowActions.quickProvider?.let { provider ->
      val baseIcon = providerIcon(provider) ?: AllIcons.General.Add
      if (rowActions.quickLaunchMode == AgentSessionLaunchMode.YOLO) {
        withYoloModeBadge(baseIcon)
      } else baseIcon
    }
    val hoveredKind = hoveredRowAction?.takeIf { it.row == row }?.kind
    return SessionTreeRowActionPresentation(
      showLoadingAction = showLoadingAction,
      quickIcon = quickIcon,
      showQuickAction = showInteractiveActions && rowActions.quickProvider != null,
      showPopupAction = showInteractiveActions,
      hoveredKind = hoveredKind,
    )
  }

  fun handleClick(point: Point): Boolean {
    val hit = rowActionAtPoint(point) ?: return false
    if (!tree.selectionModel.isRowSelected(hit.row)) {
      tree.setSelectionRow(hit.row)
    }

    when (hit.kind) {
      RowActionKind.QuickCreate -> {
        val provider = hit.actions.quickProvider ?: return false
        onQuickCreate(hit.actions.path, provider, hit.actions.quickLaunchMode)
      }

      RowActionKind.ShowPopup -> {
        val popupRect = hit.rects.popupRect ?: return false
        onShowPopup(hit.nodeId, hit.node, popupRect, hit.row)
      }
    }
    return true
  }

  fun updateHover(point: Point) {
    val previous = hoveredRowAction
    val next = rowActionAtPoint(point)
    if (previous == next) return

    hoveredRowAction = next
    previous?.let { TreeUtil.repaintRow(tree, it.row) }
    next?.let { TreeUtil.repaintRow(tree, it.row) }
    tree.cursor = if (next != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
  }

  fun clearHover() {
    val previous = hoveredRowAction
    hoveredRowAction = null
    previous?.let { TreeUtil.repaintRow(tree, it.row) }
    tree.cursor = Cursor.getDefaultCursor()
  }

  fun pinPopupRow(row: Int) {
    popupPinnedRow = row
    TreeUtil.repaintRow(tree, row)
  }

  fun clearPopupPinnedRow(row: Int) {
    if (popupPinnedRow != row) return
    popupPinnedRow = null
    TreeUtil.repaintRow(tree, row)
  }

  fun clearTransientState() {
    popupPinnedRow = null
    clearHover()
  }

  fun paint(graphics: Graphics) {
    val g2 = graphics.create() as? Graphics2D ?: return
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      val visibleRows = visibleRowRange() ?: return
      for (row in visibleRows) {
        val path = tree.getPathForRow(row) ?: continue
        val treeNode = path.lastPathComponent?.let(::extractSessionTreeId)?.let(nodeResolver) ?: continue
        val presentation = rowActionPresentation(
          row = row,
          treeNode = treeNode,
          selected = tree.selectionModel.isRowSelected(row),
        ) ?: continue
        val rects = rowActionRects(row = row, presentation = presentation) ?: continue

        val loadingRect = rects.loadingRect
        if (loadingRect != null) {
          paintIconCentered(AnimatedIcon.Default.INSTANCE, loadingRect, g2)
        }

        val quickRect = rects.quickRect
        if (quickRect != null && presentation.quickIcon != null) {
          paintRowActionSlot(g2, quickRect, hover = presentation.hoveredKind == RowActionKind.QuickCreate)
          paintIconCentered(presentation.quickIcon, quickRect, g2)
        }

        val popupRect = rects.popupRect
        if (popupRect != null) {
          paintRowActionSlot(g2, popupRect, hover = presentation.hoveredKind == RowActionKind.ShowPopup)
          paintIconCentered(LayeredIcon.ADD_WITH_DROPDOWN, popupRect, g2)
        }
      }
    }
    finally {
      g2.dispose()
    }
  }

  private fun rowActionRects(row: Int, presentation: SessionTreeRowActionPresentation): RowActionRects? {
    val bounds = tree.getRowBounds(row) ?: return null
    if (!tree.visibleRect.intersects(bounds)) return null

    val viewportLayout = resolveSessionTreeViewportLayout(tree)
    val slot = sessionTreeActionSlotSize()
    val rightGap = sessionTreeActionRightGap()
    val gap = sessionTreeActionGap()
    val y = bounds.y + (bounds.height - slot) / 2

    var right = sessionTreeRowActionsRightBoundary(
      helperX = viewportLayout.x,
      helperWidth = viewportLayout.width,
      helperRightMargin = viewportLayout.rightMargin,
      rightGap = rightGap,
      selectionRightInset = viewportLayout.selectionRightInset,
    )

    fun consumeSlot(show: Boolean): Rectangle? {
      if (!show) return null
      val rect = Rectangle(right - slot, y, slot, slot)
      right = rect.x - gap
      return rect
    }

    val popupRect = consumeSlot(show = presentation.showPopupAction)
    val quickRect = consumeSlot(show = presentation.showQuickAction)
    val loadingRect = consumeSlot(show = presentation.showLoadingAction)
    return RowActionRects(
      loadingRect = loadingRect,
      quickRect = quickRect,
      popupRect = popupRect,
    )
  }

  private fun rowActionAtPoint(point: Point): RowActionHit? {
    val row = TreeUtil.getRowForLocation(tree, point.x, point.y)
    if (row < 0) return null
    val canShowActions = tree.selectionModel.isRowSelected(row) || TreeHoverListener.getHoveredRow(tree) == row || popupPinnedRow == row
    if (!canShowActions) return null

    val path = tree.getPathForRow(row) ?: return null
    val treeId = path.lastPathComponent?.let(::extractSessionTreeId) ?: return null
    val treeNode = nodeResolver(treeId) ?: return null
    val rowActions = resolveNewSessionRowActions(treeNode, lastUsedProvider(), lastUsedLaunchMode()) ?: return null
    val presentation = rowActionPresentation(
      row = row,
      treeNode = treeNode,
      selected = tree.selectionModel.isRowSelected(row),
    ) ?: return null
    val rects = rowActionRects(row = row, presentation = presentation) ?: return null
    val quickRect = rects.quickRect
    if (quickRect != null && quickRect.contains(point)) {
      return RowActionHit(row = row, nodeId = treeId, node = treeNode, kind = RowActionKind.QuickCreate, actions = rowActions, rects = rects)
    }
    val popupRect = rects.popupRect
    if (popupRect != null && popupRect.contains(point)) {
      return RowActionHit(row = row, nodeId = treeId, node = treeNode, kind = RowActionKind.ShowPopup, actions = rowActions, rects = rects)
    }
    return null
  }

  private fun visibleRowRange(): IntRange? {
    val rowCount = tree.rowCount
    if (rowCount <= 0) return null
    val visibleRect = tree.visibleRect
    if (visibleRect.height <= 0) return null
    val first = tree.getClosestRowForLocation(visibleRect.x, visibleRect.y).coerceIn(0, rowCount - 1)
    val last = tree.getClosestRowForLocation(visibleRect.x, visibleRect.y + visibleRect.height - 1).coerceIn(first, rowCount - 1)
    return first..last
  }

  private fun paintRowActionSlot(graphics: Graphics2D, rect: Rectangle, hover: Boolean) {
    if (!hover) return
    val arc = JBUI.scale(8)
    graphics.color = JBColor.namedColor("ActionButton.hoverBackground", JBColor(0xE6EEF7, 0x4F5B66))
    graphics.fillRoundRect(rect.x, rect.y, rect.width, rect.height, arc, arc)
  }

  private fun paintIconCentered(icon: Icon, rect: Rectangle, graphics: Graphics2D) {
    val size = JBUI.scale(SESSION_TREE_ACTION_ICON_SIZE)
    val scaledIcon = IconUtil.toSize(icon, size, size)
    val x = rect.x + (rect.width - scaledIcon.iconWidth) / 2
    val y = rect.y + (rect.height - scaledIcon.iconHeight) / 2
    scaledIcon.paintIcon(tree, graphics, x, y)
  }
}
