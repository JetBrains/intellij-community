// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.accessibility.AccessibleContextDelegate
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import java.awt.Container
import javax.accessibility.Accessible
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer

abstract class ChangesTreeCellRenderer<C : JComponent>(private val textRenderer: ChangesBrowserNodeRenderer,
                                                       protected val component: C) :
  BorderLayoutPanel(), TreeCellRenderer {

  init {
    addToLeft(component)
    addToCenter(textRenderer)
    isOpaque = false
  }

  protected abstract fun C.prepare(tree: ChangesTree, node: ChangesBrowserNode<*>)

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
    component.apply {
      background = null
      isOpaque = false
      prepare(tree, value as ChangesBrowserNode<*>)
    }
    revalidate()

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
