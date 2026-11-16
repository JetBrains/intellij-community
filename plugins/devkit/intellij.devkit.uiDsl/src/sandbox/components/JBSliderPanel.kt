// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBSlider
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

private const val MIN_VALUE = 0
private const val MAX_VALUE = 100
private const val SAMPLE_VALUE = 60
private const val MINOR_TICK_SPACING = 5
private const val MAJOR_TICK_SPACING = 25

internal class JBSliderPanel : UISandboxPanel {

  override val title: String = "JBSlider"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.jb.slider.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states.and.variants")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.jb.slider.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.jb.slider.label.default")) {
          cell(createSlider())
        }
        row(DevkitUiDslBundle.message("sandbox.jb.slider.label.ticks")) {
          cell(createSlider().apply {
            minorTickSpacing = MINOR_TICK_SPACING
            majorTickSpacing = MAJOR_TICK_SPACING
            paintTicks = true
            paintLabels = true
          })
        }
        row(DevkitUiDslBundle.message("sandbox.jb.slider.label.disabled")) {
          cell(createSlider().apply { isEnabled = false })
        }
      }

      group(DevkitUiDslBundle.message("sandbox.jb.slider.group.ui.dsl")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.jb.slider.ui.dsl.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.jb.slider.label.ui.dsl")) {
          slider(MIN_VALUE, MAX_VALUE, MINOR_TICK_SPACING, MAJOR_TICK_SPACING)
            .applyToComponent { value = SAMPLE_VALUE }
        }
      }
    }
  }

  private fun createSlider(): JBSlider {
    return JBSlider(MIN_VALUE, MAX_VALUE, SAMPLE_VALUE)
  }
}
