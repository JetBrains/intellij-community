// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.FontUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

@ApiStatus.Experimental
class VideoPromoComponent(private val component: JComponent,
                          label: @Nls String?,
                          private val alwaysDisplayLabel: Boolean,
                          darkLabel: Boolean,
                          private val action: Runnable) : JBLayeredPane() {

  private val labelComponent: JComponent?

  init {
    isFullOverlayLayout = true
    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

    if (label == null) {
      labelComponent = null
    }
    else {
      labelComponent = panel {
        row {
          label("\u25B6${FontUtil.spaceAndThinSpace()}$label")
            .align(Align.CENTER)
            .applyToComponent {
              isOpaque = true
              border = JBUI.Borders.empty(8)
              if (darkLabel) {
                background = Color(0, 0, 0, 180)
                foreground = Color.WHITE
              }
              else {
                background = Color(255, 255, 255, 180)
                foreground = Color.BLACK
              }
            }
        }.resizableRow()
      }.apply {
        isVisible = alwaysDisplayLabel
        isOpaque = false
      }
      add(labelComponent)
    }
    add(component)

    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent?) {
        updatePromoLabel(true)
      }

      override fun mouseExited(e: MouseEvent?) {
        updatePromoLabel(false)
      }

      override fun mouseClicked(e: MouseEvent?) {
        action.run()
      }
    })
  }

  override fun getPreferredSize(): Dimension {
    return component.preferredSize
  }

  private fun updatePromoLabel(visible: Boolean) {
    if (!alwaysDisplayLabel) {
      labelComponent?.isVisible = visible
    }
  }
}
