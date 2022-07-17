// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.ClickListener
import com.intellij.ui.ComponentUtil
import com.intellij.ui.LayeredIcon
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.SwingConstants
import javax.swing.tree.TreePath

abstract class HoverChangesTree(project: Project, showCheckboxesBoolean: Boolean, highlightProblems: Boolean)
  : ChangesTree(project, showCheckboxesBoolean, highlightProblems) {

  private var hoverData: HoverData? = null
    set(value) {
      if (field != value) {
        field = value
        repaint()
      }
    }

  init {
    val nodeRenderer = ChangesBrowserNodeRenderer(myProject, { isShowFlatten }, highlightProblems)
    setCellRenderer(MyTreeRenderer(nodeRenderer))
    MyMouseListener().also {
      addMouseMotionListener(it)
      addMouseListener(it)
    }
    MyClickListener().installOn(this)
  }

  abstract fun getHoverIcon(node: ChangesBrowserNode<*>): HoverIcon?

  private fun getHoverData(point: Point): HoverData? {
    val path = TreeUtil.getPathForLocation(this, point.x, point.y) ?: return null
    val node = path.lastPathComponent as? ChangesBrowserNode<*> ?: return null
    val hoverIcon = getHoverIcon(node) ?: return null
    val componentBounds = getComponentBounds(path, hoverIcon.icon) ?: return null

    return HoverData(node, componentBounds.contains(point), hoverIcon)
  }

  private fun getComponentBounds(path: TreePath, icon: Icon): Rectangle? {
    val bounds = getPathBounds(path) ?: return null
    val componentWidth = getComponentWidth(icon)
    bounds.setLocation(getComponentXCoordinate(componentWidth), bounds.y)
    bounds.setSize(componentWidth, bounds.height)
    return bounds
  }

  private fun getComponentXCoordinate(componentWidth: Int): Int {
    return visibleRect.width + visibleRect.x - componentWidth
  }

  private fun getComponentWidth(icon: Icon): Int {
    return icon.iconWidth + getTransparentScrollbarWidth()
  }

  private class MyTreeRenderer(renderer: ChangesBrowserNodeRenderer) : ChangesTreeCellRenderer(renderer) {
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
      floatingIcon = prepareIcon(tree as HoverChangesTree, value as ChangesBrowserNode<*>, row, selected, hasFocus)
      return rendererComponent
    }

    fun prepareIcon(tree: HoverChangesTree, node: ChangesBrowserNode<*>, row: Int, selected: Boolean, hasFocus: Boolean): FloatingIcon? {
      if (tree.expandableItemsHandler.expandedItems.contains(row)) return null

      val hoverData = tree.hoverData
      val hovered = hoverData?.node == node
      if (!hovered && !(selected && hasFocus)) return null

      val baseIcon = when {
        hovered -> hoverData!!.hoverIcon.icon
        selected -> tree.getHoverIcon(node)?.icon ?: return null
        else -> return null
      }

      val foreground = when {
        hovered && hoverData!!.isOverOperationIcon -> baseIcon
        else -> IconLoader.getDisabledIcon(baseIcon, this)
      }

      val componentWidth = tree.getComponentWidth(foreground)
      val componentHeight = tree.getRowHeight(this)
      val background = ColorIcon(componentWidth, componentHeight, componentWidth, componentHeight,
                                 tree.getBackground(row, selected), false)

      val icon = LayeredIcon(2).apply {
        setIcon(background, 0)
        setIcon(foreground, 1, SwingConstants.WEST)
      }

      val location = tree.getComponentXCoordinate(componentWidth) - (TreeUtil.getNodeRowX(tree, row) + tree.insets.left)

      return FloatingIcon(icon, location)
    }

    private data class FloatingIcon(val icon: Icon, val location: Int)
  }

  private inner class MyMouseListener : MouseAdapter() {
    override fun mouseMoved(e: MouseEvent) {
      setHoverData(getHoverData(e.point))
    }

    override fun mouseExited(e: MouseEvent) {
      setHoverData(null)
    }

    private fun setHoverData(data: HoverData?) {
      hoverData = data
      if (data != null && data.isOverOperationIcon) {
        toolTipText = data.hoverIcon.tooltip
        expandableItemsHandler.isEnabled = false
      }
      else {
        toolTipText = null
        expandableItemsHandler.isEnabled = true
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
      if (selected) return RenderingUtil.getBackground(this, selected)
      return getPathForRow(row)?.let { path -> getPathBackground(path, row) } ?: RenderingUtil.getBackground(this, selected)
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