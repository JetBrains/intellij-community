// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ClickListener
import com.intellij.ui.ComponentUtil
import com.intellij.ui.LayeredIcon
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.tree.TreePath

abstract class HoverChangesTree(val tree: ChangesTree) {

  private var _hoverData: HoverData? = null
    set(value) {
      if (field != value) {
        field = value
        tree.repaint()
      }
    }

  fun install() {
    val nodeRenderer = ChangesBrowserNodeRenderer(tree.project, { tree.isShowFlatten }, tree.isHighlightProblems)
    tree.cellRenderer = HoverChangesTreeRenderer(nodeRenderer)
    MyMouseListener().also {
      tree.addMouseMotionListener(it)
      tree.addMouseListener(it)
    }
    MyClickListener().installOn(tree)
  }

  abstract fun getHoverIcon(node: ChangesBrowserNode<*>): HoverIcon?

  private fun getHoverData(point: Point): HoverData? {
    val path = TreeUtil.getPathForLocation(tree, point.x, point.y) ?: return null
    val node = path.lastPathComponent as? ChangesBrowserNode<*> ?: return null
    val hoverIcon = getHoverIcon(node) ?: return null
    val componentBounds = getComponentBounds(path, hoverIcon.icon) ?: return null

    return HoverData(node, componentBounds.contains(point), hoverIcon)
  }

  private fun getComponentBounds(path: TreePath, icon: Icon): Rectangle? {
    val bounds = tree.getPathBounds(path) ?: return null
    val componentWidth = getComponentWidth(icon)
    bounds.setLocation(getComponentXCoordinate(componentWidth), bounds.y)
    bounds.setSize(componentWidth, bounds.height)
    return bounds
  }

  private fun getComponentXCoordinate(componentWidth: Int): Int {
    return tree.visibleRect.width + tree.visibleRect.x - componentWidth
  }

  private fun getComponentWidth(icon: Icon): Int {
    val transparentScrollbarWidth = tree.getTransparentScrollbarWidth()
    val borderWidth = if (transparentScrollbarWidth == 0) JBUI.scale(2) else 0
    return icon.iconWidth + transparentScrollbarWidth + borderWidth
  }

  private inner class HoverChangesTreeRenderer(renderer: ChangesBrowserNodeRenderer) : ChangesTreeCellRenderer(renderer) {
    private var floatingIcon: FloatingIcon? = null

    override fun paint(g: Graphics) {
      super.paint(g)
      floatingIcon?.let {
        it.icon.paintIcon(this, g, it.location, 0)
      }
    }

    override fun getTreeCellRendererComponent(tree: JTree,
                                              value: Any,
                                              selected: Boolean,
                                              expanded: Boolean,
                                              leaf: Boolean,
                                              row: Int,
                                              hasFocus: Boolean): Component {
      val rendererComponent = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
      floatingIcon = prepareIcon(tree as ChangesTree, value as ChangesBrowserNode<*>, row, selected)
      return rendererComponent
    }

    fun prepareIcon(tree: ChangesTree, node: ChangesBrowserNode<*>, row: Int, selected: Boolean): FloatingIcon? {
      if (tree.expandableItemsHandler.expandedItems.contains(row)) return null

      val hoverData = _hoverData
      val hovered = hoverData?.node == node
      if (!hovered && !(selected && tree.leadSelectionRow == row)) return null

      val foreground = when {
        hovered -> hoverData!!.hoverIcon.icon
        selected -> getHoverIcon(node)?.icon ?: return null
        else -> return null
      }

      val componentWidth = getComponentWidth(foreground)
      val componentHeight = tree.getRowHeight(this)
      val background = ColorIcon(componentWidth, componentHeight, componentWidth, componentHeight,
                                 tree.getBackground(row, selected), false)

      val icon = if (hovered && hoverData!!.isOverOperationIcon) {
        val highlight = ColorIcon(foreground.iconWidth, componentHeight, foreground.iconWidth, foreground.iconHeight,
                                  JBUI.CurrentTheme.ActionButton.hoverBackground(),
                                  JBUI.CurrentTheme.ActionButton.hoverBorder(), JBUI.scale(4))
        createLayeredIcon(background, highlight, foreground)
      }
      else {
        createLayeredIcon(background, foreground)
      }

      val location = getComponentXCoordinate(componentWidth) - (TreeUtil.getNodeRowX(tree, row) + tree.insets.left)

      return FloatingIcon(icon, location)
    }

    private fun createLayeredIcon(background: Icon, vararg foreground: Icon): Icon {
      return LayeredIcon(foreground.size + 1).apply {
        setIcon(background, 0)
        for ((i, f) in foreground.withIndex()) {
          setIcon(f, i + 1, SwingConstants.WEST)
        }
      }
    }
  }

  private data class FloatingIcon(val icon: Icon, val location: Int)

  private inner class MyMouseListener : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      setHoverData(getHoverData(e.point))
    }

    override fun mouseExited(e: MouseEvent) {
      setHoverData(null)
    }

    private fun setHoverData(data: HoverData?) {
      _hoverData = data
      if (data != null && data.isOverOperationIcon) {
        tree.toolTipText = data.hoverIcon.tooltip
        tree.expandableItemsHandler.isEnabled = false
      }
      else {
        tree.toolTipText = null
        tree.expandableItemsHandler.isEnabled = true
      }
    }
  }

  private inner class MyClickListener : ClickListener() {
    override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
      val hoverData = getHoverData(event.point) ?: return false
      if (!hoverData.isOverOperationIcon) return false
      hoverData.hoverIcon.invokeAction(hoverData.node)
      return true
    }
  }

  private data class HoverData(val node: ChangesBrowserNode<*>,
                               val isOverOperationIcon: Boolean,
                               val hoverIcon: HoverIcon)

  companion object {
    fun Tree.getBackground(row: Int, selected: Boolean): Color {
      if (selected) return RenderingUtil.getBackground(this, true)
      return getPathForRow(row)?.let { path -> getPathBackground(path, row) } ?: RenderingUtil.getBackground(this, false)
    }

    fun ChangesTree.getRowHeight(renderer: ChangesTreeCellRenderer): Int {
      return if (isFixedRowHeight) rowHeight else renderer.preferredSize.height
    }

    fun Component.getTransparentScrollbarWidth(): Int {
      val scrollBar = ComponentUtil.getScrollPane(this)?.verticalScrollBar
      val hasTransparentScrollbar = scrollBar != null && scrollBar.isVisible && !scrollBar.isOpaque
      if (hasTransparentScrollbar) return UIUtil.getScrollBarWidth()
      return 0
    }
  }
}

/**
 * Implement [equals] to avoid unnecessary tree repaints on mouse movements.
 */
abstract class HoverIcon(val icon: Icon,
                         val tooltip: @NlsContexts.Tooltip String?) {
  abstract fun invokeAction(node: ChangesBrowserNode<*>)
}