// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.accessibility.AccessibleContextDelegate
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import java.awt.Container
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

private class ChangesTreeCellRenderer(private val textRenderer: ChangesBrowserNodeRenderer) :
  BorderLayoutPanel(), TreeCellRenderer {

  private val checkBox = ThreeStateCheckBox()

  init {
    addToLeft(checkBox)
    addToCenter(textRenderer)
    isOpaque = false
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
    background = null

    textRenderer.apply {
      isOpaque = false
      isTransparentIconBackground = true
      toolTipText = null
      getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
    }
    checkBox.apply {
      background = null
      isOpaque = false

      val node = value as ChangesBrowserNode<*>
      isVisible = tree.run { isShowCheckboxes && isInclusionVisible(node) }
      if (isVisible) {
        state = tree.getNodeStatus(node)
        isEnabled = tree.run { isEnabled && isInclusionEnabled(node) }
      }
    }
    revalidate()

    return this
  }

  override fun getToolTipText(): String? = textRenderer.toolTipText

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = object : AccessibleContextDelegate(checkBox.accessibleContext) {
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
}
