// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.ColorPanel
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.panel
import java.awt.Color
import javax.swing.JComponent

private val SAMPLE_COLOR = JBColor(0xBDD3FF, 0x2E4D89)
private val SAMPLE_COLOR_WITH_ALPHA = JBColor(0x80BDD3FF.toInt(), 0x802E4D89.toInt())

internal class ColorPanelPanel : UISandboxPanel {

  override val title: String = "ColorPanel"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.color.panel.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states.and.variants")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.color.panel.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.color.panel.label.default")) {
          cell(createColorPanel(SAMPLE_COLOR))
        }
        row(DevkitUiDslBundle.message("sandbox.color.panel.label.transparent")) {
          cell(ColorPanel().apply {
            setSupportTransparency(true)
            selectedColor = SAMPLE_COLOR_WITH_ALPHA
          })
        }
        row(DevkitUiDslBundle.message("sandbox.color.panel.label.no.color")) {
          cell(createColorPanel(null))
        }
        row(DevkitUiDslBundle.message("sandbox.color.panel.label.read.only")) {
          cell(createColorPanel(SAMPLE_COLOR).apply { setEditable(false) })
        }
        row(DevkitUiDslBundle.message("sandbox.color.panel.label.disabled")) {
          cell(createColorPanel(SAMPLE_COLOR).apply { isEnabled = false })
        }
      }
    }
  }

  private fun createColorPanel(color: Color?): ColorPanel {
    return ColorPanel().apply { selectedColor = color }
  }
}
