// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.sessions.actions.AgentSessionsDirectPathNewThreadAction
import com.intellij.agent.workbench.sessions.core.statistics.AgentWorkbenchEntryPoint
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.hover.TreeHoverListener
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.JComponent

internal data class SessionTreeRowActionPresentation(
  @JvmField val showLoadingAction: Boolean,
  @JvmField val showNewThreadAction: Boolean,
) {
  val reservedWidth: Int
    get() = sessionTreeRowActionRightPadding(
      showLoadingAction = showLoadingAction,
      showNewThreadAction = showNewThreadAction,
    )
}

private data class RowActionRects(
  @JvmField val loadingRect: Rectangle?,
  @JvmField val newThreadRect: Rectangle?,
)

private data class RowActionComponent(
  @JvmField val toolbar: ActionToolbar,
  @JvmField val component: JComponent,
)

internal class AgentSessionsTreeRowActionsOverlay(
  private val project: Project,
  private val tree: Tree,
  private val nodeResolver: (SessionTreeId) -> SessionTreeNode?,
) {
  private var hoveredRow: Int? = null
  private val rowActionComponents = LinkedHashMap<SessionTreeId, RowActionComponent>()

  fun rowActionPresentation(
    row: Int,
    treeNode: SessionTreeNode,
    selected: Boolean,
  ): SessionTreeRowActionPresentation? {
    val showLoadingAction = isLoadingNode(treeNode)
    val showInteractiveAction = selected || TreeHoverListener.getHoveredRow(tree) == row || hoveredRow == row
    val showNewThreadAction = showInteractiveAction && newThreadPath(treeNode) != null
    if (!showLoadingAction && !showNewThreadAction) return null
    return SessionTreeRowActionPresentation(
      showLoadingAction = showLoadingAction,
      showNewThreadAction = showNewThreadAction,
    )
  }

  fun updateHover(point: Point) {
    val previous = hoveredRow
    val next = hoveredActionRow(point)
    if (previous == next) return

    hoveredRow = next
    previous?.let { TreeUtil.repaintRow(tree, it) }
    next?.let { TreeUtil.repaintRow(tree, it) }
  }

  fun clearHover() {
    val previous = hoveredRow
    hoveredRow = null
    previous?.let { TreeUtil.repaintRow(tree, it) }
  }

  fun clearTransientState() {
    hoveredRow = null
    removeAllRowActionComponents()
  }

  fun paint(graphics: Graphics) {
    val g2 = graphics.create() as? Graphics2D ?: return
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      val visibleRows = visibleRowRange() ?: run {
        removeAllRowActionComponents()
        return
      }
      val activeComponentIds = LinkedHashSet<SessionTreeId>()
      for (row in visibleRows) {
        val path = tree.getPathForRow(row) ?: continue
        val treeId = path.lastPathComponent?.let(::extractSessionTreeId) ?: continue
        val treeNode = nodeResolver(treeId) ?: continue
        val presentation = rowActionPresentation(
          row = row,
          treeNode = treeNode,
          selected = tree.selectionModel.isRowSelected(row),
        ) ?: continue
        val rects = rowActionRects(row = row, presentation = presentation) ?: continue

        val loadingRect = rects.loadingRect
        if (loadingRect != null) {
          paintLoadingIconCentered(loadingRect, g2)
        }

        val newThreadRect = rects.newThreadRect
        val newThreadPath = newThreadPath(treeNode)
        if (newThreadRect != null && newThreadPath != null) {
          activeComponentIds += treeId
          val rowActionComponent = rowActionComponent(treeId = treeId, path = newThreadPath)
          rowActionComponent.component.bounds = newThreadRect
          rowActionComponent.component.isVisible = true
          rowActionComponent.toolbar.updateActionsAsync()
        }
      }
      removeInactiveRowActionComponents(activeComponentIds)
    }
    finally {
      g2.dispose()
    }
  }

  private fun hoveredActionRow(point: Point): Int? {
    val row = TreeUtil.getRowForLocation(tree, point.x, point.y)
    if (row < 0) return null
    val path = tree.getPathForRow(row) ?: return null
    val treeId = path.lastPathComponent?.let(::extractSessionTreeId) ?: return null
    val treeNode = nodeResolver(treeId) ?: return null
    return row.takeIf { newThreadPath(treeNode) != null || isLoadingNode(treeNode) }
  }

  private fun rowActionRects(row: Int, presentation: SessionTreeRowActionPresentation): RowActionRects? {
    val bounds = tree.getRowBounds(row) ?: return null
    if (!tree.visibleRect.intersects(bounds)) return null

    val viewportLayout = resolveSessionTreeViewportLayout(tree)
    val slot = sessionTreeActionSlotSize()
    val rightGap = sessionTreeActionRightGap()
    val gap = sessionTreeActionGap()
    val loadingY = bounds.y + (bounds.height - slot) / 2
    val buttonWidth = sessionTreeNewThreadActionWidth()
    val buttonHeight = sessionTreeNewThreadActionHeight()
    val buttonY = bounds.y + (bounds.height - buttonHeight) / 2

    var right = sessionTreeRowActionsRightBoundary(
      helperX = viewportLayout.x,
      helperWidth = viewportLayout.width,
      helperRightMargin = viewportLayout.rightMargin,
      rightGap = rightGap,
      selectionRightInset = viewportLayout.selectionRightInset,
    )

    val newThreadRect = if (presentation.showNewThreadAction) {
      Rectangle(right - buttonWidth, buttonY, buttonWidth, buttonHeight).also { rect ->
        right = rect.x - gap
      }
    }
    else {
      null
    }

    val loadingRect = if (presentation.showLoadingAction) {
      Rectangle(right - slot, loadingY, slot, slot)
    }
    else {
      null
    }

    return RowActionRects(
      loadingRect = loadingRect,
      newThreadRect = newThreadRect,
    )
  }

  private fun rowActionComponent(treeId: SessionTreeId, path: String): RowActionComponent {
    rowActionComponents[treeId]?.let { return it }

    val action = AgentSessionsDirectPathNewThreadAction(
      project = project,
      targetPath = { path },
      quickStartEntryPoint = AgentWorkbenchEntryPoint.TREE_ROW_OVERLAY,
      popupEntryPoint = AgentWorkbenchEntryPoint.TREE_POPUP,
      minimumButtonSize = ::sessionTreeNewThreadActionButtonSize,
      beforeAction = { selectRow(treeId) },
    )
    val toolbar = ActionManager.getInstance().createActionToolbar(
      ActionPlaces.TOOLWINDOW_CONTENT,
      DefaultActionGroup(action),
      true,
    )
    toolbar.setTargetComponent(tree)
    toolbar.setMiniMode(true)
    toolbar.setMinimumButtonSize(sessionTreeNewThreadActionButtonSize())
    toolbar.setReservePlaceAutoPopupIcon(false)
    val component = toolbar.component
    component.isOpaque = false
    component.isVisible = false
    tree.add(component)
    val rowActionComponent = RowActionComponent(toolbar = toolbar, component = component)
    rowActionComponents[treeId] = rowActionComponent
    return rowActionComponent
  }

  private fun selectRow(treeId: SessionTreeId) {
    for (row in 0 until tree.rowCount) {
      val path = tree.getPathForRow(row) ?: continue
      if (path.lastPathComponent?.let(::extractSessionTreeId) == treeId) {
        tree.setSelectionRow(row)
        return
      }
    }
  }

  private fun removeInactiveRowActionComponents(activeIds: Set<SessionTreeId>) {
    val iterator = rowActionComponents.iterator()
    while (iterator.hasNext()) {
      val (treeId, rowActionComponent) = iterator.next()
      if (treeId in activeIds) continue
      tree.remove(rowActionComponent.component)
      iterator.remove()
    }
  }

  private fun removeAllRowActionComponents() {
    rowActionComponents.values.forEach { tree.remove(it.component) }
    rowActionComponents.clear()
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

  private fun paintLoadingIconCentered(rect: Rectangle, graphics: Graphics2D) {
    val icon = AnimatedIcon.Default.INSTANCE
    val x = rect.x + (rect.width - icon.iconWidth) / 2
    val y = rect.y + (rect.height - icon.iconHeight) / 2
    icon.paintIcon(tree, graphics, x, y)
  }
}

private fun isLoadingNode(node: SessionTreeNode): Boolean {
  return when (node) {
    is SessionTreeNode.Project -> node.project.isLoading
    is SessionTreeNode.Worktree -> node.worktree.isLoading
    else -> false
  }
}

private fun newThreadPath(node: SessionTreeNode): String? {
  return when (node) {
    is SessionTreeNode.Project -> node.project.path
    is SessionTreeNode.Worktree -> node.worktree.path
    is SessionTreeNode.Thread,
    is SessionTreeNode.SubAgent,
    is SessionTreeNode.Warning,
    is SessionTreeNode.Error,
    is SessionTreeNode.Empty,
    is SessionTreeNode.MoreProjects,
    is SessionTreeNode.MoreThreads,
      -> null
  }
}
