// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ui.CellRendererPanel
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.accessibility.AccessibleContextDelegate
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

abstract class ChangesTreeCellRenderer<C : JComponent>(
  private val textRenderer: ChangesBrowserNodeRenderer,
  protected val component: C
) : CellRendererPanel(),
    TreeCellRenderer {

  init {
    buildLayout()
  }

  protected abstract fun C.prepare(tree: ChangesTree, node: ChangesBrowserNode<*>)

  private fun buildLayout() {
    layout = BorderLayout()

    add(component, BorderLayout.WEST)
    add(textRenderer, BorderLayout.CENTER)
  }

  /**
   * Otherwise incorrect node sizes are cached - see [com.intellij.ui.tree.ui.DefaultTreeUI.createNodeDimensions].
   * And [com.intellij.ui.ExpandableItemsHandler] does not work correctly.
   */
  override fun getPreferredSize(): Dimension = layout.preferredLayoutSize(this)

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
    background = null
    isSelected = selected

    textRenderer.apply {
      isOpaque = false
      isTransparentIconBackground = true
      toolTipText = null
      getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    }
    component.apply {
      background = null
      isOpaque = false
      prepare(tree, value as ChangesBrowserNode<*>)
    }

    return this
  }

  override fun getToolTipText(): String? = textRenderer.toolTipText

  override fun getAccessibleContext(): AccessibleContext {
    val accessibleComponent = component as? Accessible ?: return super.getAccessibleContext()

    if (accessibleContext == null) {
      accessibleContext = object : AccessibleContextDelegate(accessibleComponent.accessibleContext) {
        override fun getDelegateParent(): Container? = parent

        override fun getAccessibleName(): String? {
          accessibleComponent.accessibleContext.accessibleName = textRenderer.accessibleContext.accessibleName
          return accessibleComponent.accessibleContext.accessibleName
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
}

class CheckboxTreeCellRenderer(textRenderer: ChangesBrowserNodeRenderer) :
  ChangesTreeCellRenderer<ThreeStateCheckBox>(textRenderer, ThreeStateCheckBox()) {

  override fun ThreeStateCheckBox.prepare(tree: ChangesTree, node: ChangesBrowserNode<*>) {
    isVisible = tree.run { isShowCheckboxes && isInclusionVisible(node) }
    if (isVisible) {
      state = tree.getNodeStatus(node)
      isEnabled = tree.run { isEnabled && isInclusionEnabled(node) }
    }
  }
}
