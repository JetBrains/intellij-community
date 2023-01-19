// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.ui.CellRendererPanel
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu
import java.awt.*
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

open class ChangesTreeCellRenderer(protected val textRenderer: ChangesBrowserNodeRenderer) : CellRendererPanel(), TreeCellRenderer {
  private val checkBox = ThreeStateCheckBox()

  init {
    buildLayout()
  }

  private fun buildLayout() {
    layout = BorderLayout()

    add(checkBox, BorderLayout.WEST)
    add(textRenderer, BorderLayout.CENTER)
  }

  override fun getTreeCellRendererComponent(
    tree: JTree,
    value: Any,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean
  ): Component {
    tree as ChangesTree
    value as ChangesBrowserNode<*>

    background = null
    isSelected = selected

    textRenderer.apply {
      isOpaque = false
      isTransparentIconBackground = true
      toolTipText = null
      getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    }
    checkBox.apply {
      background = null
      isOpaque = false

      isVisible = tree.isShowCheckboxes &&
                  (value is FixedHeightSampleChangesBrowserNode || // assume checkbox is visible for the sample node
                   tree.isInclusionVisible(value))
      if (isVisible) {
        state = tree.getNodeStatus(value)
        isEnabled = tree.run { isEnabled && isInclusionEnabled(value) }
      }
      else {
        state = ThreeStateCheckBox.State.NOT_SELECTED
        isEnabled = false
      }
    }

    return this
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = object : AccessibleContextDelegateWithContextMenu(checkBox.accessibleContext) {
        override fun doShowContextMenu() {
          ActionManager.getInstance().tryToExecute(ActionManager.getInstance().getAction("ShowPopupMenu"), null, null, null, true)
        }

        override fun getDelegateParent(): Container? = parent

        override fun getAccessibleName(): String? {
          checkBox.accessibleContext.accessibleName = textRenderer.accessibleContext.accessibleName
          return checkBox.accessibleContext.accessibleName
        }

        override fun getAccessibleRole(): AccessibleRole {
          // Because of a problem with NVDA we have to make this a LABEL,
          // or otherwise NVDA will read out the entire tree path, causing confusion.
          return AccessibleRole.LABEL
        }
      }
    }
    return accessibleContext
  }

  /**
   * In case of New UI background selection painting performs by [com.intellij.ui.tree.ui.DefaultTreeUI.paint],
   * but in case of expansion popup painting it is necessary to fill the background in renderer.
   *
   * [setOpaque] for renderer is set in the tree UI and in [com.intellij.ui.TreeExpandableItemsHandler]
   *
   * @see [com.intellij.ui.tree.ui.DefaultTreeUI.setBackground] and its private overloading
   * @see [com.intellij.ui.TreeExpandableItemsHandler.doPaintTooltipImage]
   */
  final override fun paintComponent(g: Graphics?) {
    if (isOpaque) {
      super.paintComponent(g)
    }
  }

  /**
   * Otherwise incorrect node sizes are cached - see [com.intellij.ui.tree.ui.DefaultTreeUI.createNodeDimensions].
   * And [com.intellij.ui.ExpandableItemsHandler] does not work correctly.
   */
  override fun getPreferredSize(): Dimension = layout.preferredLayoutSize(this)

  override fun getToolTipText(): String? = textRenderer.toolTipText
}