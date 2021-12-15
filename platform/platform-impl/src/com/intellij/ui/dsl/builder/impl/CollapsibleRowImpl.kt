// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.CollapsibleRow
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.border.EmptyBorder

@ApiStatus.Internal
internal class CollapsibleRowImpl(dialogPanelConfig: DialogPanelConfig,
                                  panelContext: PanelContext,
                                  parent: PanelImpl,
                                  @NlsContexts.BorderTitle title: String,
                                  init: Panel.() -> Unit) :
  RowImpl(dialogPanelConfig, panelContext, parent, RowLayout.INDEPENDENT), CollapsibleRow {

  private val collapsibleTitledSeparator = CollapsibleTitledSeparator(title)

  override var expanded by collapsibleTitledSeparator::expanded

  init {
    collapsibleTitledSeparator.setLabelFocusable(true)
    (collapsibleTitledSeparator.label.border as? EmptyBorder)?.borderInsets?.let {
      collapsibleTitledSeparator.putClientProperty(DslComponentProperty.VISUAL_PADDINGS,
                                                   Gaps(top = it.top, left = it.left, bottom = it.bottom))
    }
    collapsibleTitledSeparator.label.addKeyListener(object : KeyAdapter() {
      override fun keyTyped(e: KeyEvent?) {
        if (e?.keyChar == ' ') {
          expanded = !expanded
        }
      }
    })
    val collapsibleTitledSeparator = this.collapsibleTitledSeparator
    panel {
      row {
        cell(collapsibleTitledSeparator).horizontalAlign(HorizontalAlign.FILL)
      }
      val expandablePanel = panel {
        init()
      }
      collapsibleTitledSeparator.onAction {
        expandablePanel.visible(it)
      }
    }
  }
}
