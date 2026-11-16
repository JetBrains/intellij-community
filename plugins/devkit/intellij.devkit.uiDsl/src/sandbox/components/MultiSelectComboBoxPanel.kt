// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.MultiSelectComboBox
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private val LANGUAGES: List<@NonNls String> = listOf("Java", "Kotlin", "Python", "Go", "Rust", "TypeScript")

internal class MultiSelectComboBoxPanel : UISandboxPanel {

  override val title: String = "MultiSelectComboBox"

  override val isInternalApi: Boolean = true

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.multi.select.combo.box.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.multi.select.combo.box.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.multi.select.combo.box.label.selected")) {
          cell(createComboBox().apply { setSelectedItems(LANGUAGES.take(2)) })
        }
        row(DevkitUiDslBundle.message("sandbox.multi.select.combo.box.label.empty")) {
          cell(createComboBox())
        }
      }
    }
  }

  private fun createComboBox(): MultiSelectComboBox<String> {
    return MultiSelectComboBox(LANGUAGES) { it }
  }
}
