// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.keymap.impl.ui

import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

private const val TEXT_SHORTCUTS_GAP = 6
private const val PREFERRED_TEXT_WIDTH_RATIO = 0.3

/**
 * Reworked version of UI keymaps renderer for the new UI only
 */
internal class NewKeymapsRenderer(private val actionsTree: ActionsTree) : ActionsTree.KeymapsRenderer(actionsTree) {

  companion object {

    @JvmStatic
    val isFeatureEnabled: Boolean
      get() = ExperimentalUI.isNewUI() && Registry.`is`("ide.ui.keymap.ijpl187594", true)
  }

  private var data: ShortcutsData? = null

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
      data = null
    }
    else {
      val rendererHelper = ActionsTreeRendererHelper(userObject, actionsTree.myKeymap)
      if (rendererHelper.shortcuts.isEmpty() && rendererHelper.abbreviations.isEmpty()) {
        data = null
      }
      else {
        val shortcutTextList = ShortcutTextList(rendererHelper.shortcuts, rendererHelper.abbreviations, tree, -1)
        data = ShortcutsData(rendererHelper, shortcutTextList)
      }
    }
  }

  /**
   * Squeezing rules when there is not enough width for shortcuts:
   *
   * - If the text occupies less than [PREFERRED_TEXT_WIDTH_RATIO] of the available width, squeeze only the shortcuts
   * - Otherwise: squeeze the shortcuts till `1 - `[PREFERRED_TEXT_WIDTH_RATIO] and fill the remaining by the text.
   * In this case, the text can occupy more than [PREFERRED_TEXT_WIDTH_RATIO].
   */
  override fun doPaint(g: Graphics2D) {
    val data = data
    if (isLinkOrSeparator || data == null) {
      super.doPaint(g)
      return
    }

    val availableWidth = width - extraGaps()
    val textPreferredWidth = super.getPreferredSize().width
    val shortcutsPreferredWidth = data.shortcutTextList.getWidth()

    if (textPreferredWidth + shortcutsPreferredWidth <= availableWidth) {
      super.doPaint(g)
      paintShortcuts(g, data.shortcutTextList)
      return
    }

    val maxShortcutWidth = if (textPreferredWidth <= availableWidth * PREFERRED_TEXT_WIDTH_RATIO)
      availableWidth - textPreferredWidth
    else (availableWidth * (1 - PREFERRED_TEXT_WIDTH_RATIO)).toInt()

    val squeezedShortcutTextList = ShortcutTextList(data.rendererHelper.shortcuts, data.rendererHelper.abbreviations, tree, maxShortcutWidth)

    UIUtil.useSafely(g) {
      it.clipRect(0, 0, width - extraGaps() - squeezedShortcutTextList.getWidth(), height)
      super.doPaint(it)
    }

    paintShortcuts(g, squeezedShortcutTextList)
  }

  override fun getPreferredSize(): Dimension {
    val result = super.getPreferredSize()
    val data = data ?: return result

    return Dimension(result.width + data.shortcutTextList.getWidth() + extraGaps(), result.height)
  }

  private fun paintShortcuts(g: Graphics2D, shortcutTextList: ShortcutTextList) {
    val config = GraphicsUtil.setupAAPainting(g)
    try {
      shortcutTextList.draw(Rectangle(width - JBUIScale.scale(ActionsTree.SHORTCUTS_RIGHT_GAP), height), g)
    }
    finally {
      config.restore()
    }
  }

  private fun extraGaps(): Int {
    return JBUIScale.scale(TEXT_SHORTCUTS_GAP) + JBUIScale.scale(ActionsTree.SHORTCUTS_RIGHT_GAP)
  }
}

private data class ShortcutsData(
  val rendererHelper: ActionsTreeRendererHelper,
  val shortcutTextList: ShortcutTextList,
)
