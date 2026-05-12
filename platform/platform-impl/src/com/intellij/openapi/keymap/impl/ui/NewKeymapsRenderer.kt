// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui

import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/**
 * Reworked version of UI keymaps renderer for the new UI only
 */
internal class NewKeymapsRenderer(private val actionsTree: ActionsTree) : ActionsTree.KeymapsRenderer(actionsTree) {

  companion object {

    @JvmStatic
    val isFeatureEnabled: Boolean
      get() = ExperimentalUI.isNewUI() && Registry.`is`("ide.linux.ijpl187594", false)
  }

  private var rendererHelper: ActionsTreeRendererHelper? = null

  override fun customizeCellRenderer(
    tree: JTree,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ) {
    super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus)

    val userObject = (value as? DefaultMutableTreeNode)?.getUserObject()
    if (isLinkOrSeparator || userObject == null) {
      rendererHelper = null
    }
    else {
      rendererHelper = ActionsTreeRendererHelper(userObject, actionsTree.myKeymap)
    }
  }

  override fun doPaint(g: Graphics2D) {
    if (isLinkOrSeparator) {
      super.doPaint(g)
      return
    }

    super.doPaint(g)

    rendererHelper?.let {
      paintShortcuts(g, it)
    }
  }

  private fun paintShortcuts(g: Graphics2D, rendererHelper: ActionsTreeRendererHelper) {
    val config = GraphicsUtil.setupAAPainting(g)
    try {
      ShortcutTextList(rendererHelper.shortcuts, rendererHelper.abbreviations, tree, g)
        .draw(Rectangle(width - JBUIScale.scale(ActionsTree.SHORTCUTS_RIGHT_GAP), height), g)
    }
    finally {
      config.restore()
    }
  }
}
