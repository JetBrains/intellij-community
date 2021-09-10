// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.TitledSeparator
import com.intellij.ui.dsl.builder.CollapsiblePanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.util.ui.IndentedIcon
import com.intellij.util.ui.UIUtil
import java.awt.Cursor
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.math.max

internal class CollapsiblePanelImpl(dialogPanelConfig: DialogPanelConfig,
                                    parent: RowImpl,
                                    @NlsContexts.BorderTitle title: String,
                                    init: Panel.() -> Unit) :
  PanelImpl(dialogPanelConfig, parent), CollapsiblePanel {

  override var expanded: Boolean = true
    set(value) {
      field = value
      expandablePanel.visible(value)
      collapsibleTitledSeparator.updateIcon()
    }

  private val collapsibleTitledSeparator = CollapsibleTitledSeparator(title)
  private val expandablePanel: Panel

  init {
    val collapsibleTitledSeparator = this.collapsibleTitledSeparator
    val row = row {
      cell(collapsibleTitledSeparator).horizontalAlign(HorizontalAlign.FILL)
    }
    row.internalTopGap = dialogPanelConfig.spacing.groupTopGap
    expandablePanel = panel {
      init()
    }
  }

  private inner class CollapsibleTitledSeparator(@NlsContexts.Separator title: String) : TitledSeparator(title) {

    init {
      updateIcon()
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      addMouseListener(object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent) {
          expanded = !expanded
        }
      })
    }

    fun updateIcon() {
      val treeExpandedIcon = UIUtil.getTreeExpandedIcon()
      val treeCollapsedIcon = UIUtil.getTreeCollapsedIcon()
      val width = max(treeExpandedIcon.iconWidth, treeCollapsedIcon.iconWidth)
      var icon = if (expanded) treeExpandedIcon else treeCollapsedIcon
      val extraSpace = width - icon.iconWidth
      if (extraSpace > 0) {
        val left = extraSpace / 2
        icon = IndentedIcon(icon, Insets(0, left, 0, extraSpace - left))
      }
      label.icon = icon
      label.disabledIcon = IconLoader.getTransparentIcon(icon, 0.5f)
    }
  }
}
