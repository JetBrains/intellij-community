// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.FontComboBox
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private const val SAMPLE_FONT: @NonNls String = "JetBrains Mono"

internal class FontComboBoxPanel : UISandboxPanel {

  override val title: String = "FontComboBox"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.font.combo.box.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states.and.variants")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.font.combo.box.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.font.combo.box.label.default")) {
          cell(createComboBox())
        }
        row(DevkitUiDslBundle.message("sandbox.font.combo.box.label.monospaced")) {
          cell(createComboBox().apply { isMonospacedOnly = true })
        }
        row(DevkitUiDslBundle.message("sandbox.font.combo.box.label.disabled")) {
          cell(createComboBox().apply { isEnabled = false })
        }
      }
    }
  }

  private fun createComboBox(): FontComboBox {
    return FontComboBox().apply { fontName = SAMPLE_FONT }
  }
}
