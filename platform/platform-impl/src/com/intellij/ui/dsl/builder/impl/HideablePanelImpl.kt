// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.TitledSeparator
import com.intellij.ui.dsl.builder.HideablePanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

internal class HideablePanelImpl(dialogPanelConfig: DialogPanelConfig,
                                 parent: RowImpl,
                                 @NlsContexts.BorderTitle title: String,
                                 init: Panel.() -> Unit) :
  PanelImpl(dialogPanelConfig, parent), HideablePanel {

  override var expanded: Boolean = true
    set(value) {
      field = value
      expandablePanel.visible(value)
      hideableTitledSeparator.updateIcon()
    }

  private val hideableTitledSeparator = HideableTitledSeparator(title)
  private val expandablePanel: Panel

  init {
    val hideableTitledSeparator = this.hideableTitledSeparator
    val row = row {
      cell(hideableTitledSeparator).horizontalAlign(HorizontalAlign.FILL)
    }
    row.internalTopGap = dialogPanelConfig.spacing.groupTopGap
    expandablePanel = panel {
      init()
    }
  }

  private inner class HideableTitledSeparator(@NlsContexts.Separator title: String) : TitledSeparator(title) {

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
      val icon = if (expanded) AllIcons.General.ArrowDown else AllIcons.General.ArrowRight
      label.icon = icon
      label.disabledIcon = IconLoader.getTransparentIcon(icon, 0.5f)
    }
  }
}
