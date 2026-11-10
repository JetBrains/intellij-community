// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private val RECENT_URLS: List<@NonNls String> = listOf(
  "https://localhost:8080/api",
  "https://staging.example.com/api",
  "https://api.example.com/v1",
)

internal class TextFieldWithHistoryPanel : UISandboxPanel {

  override val title: String = "TextFieldWithHistory"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.text.field.with.history.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.text.field.with.history.group.default")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.text.field.with.history.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.text.field.with.history.label.url")) {
          cell(createField(RECENT_URLS, RECENT_URLS[0]))
        }
        row(DevkitUiDslBundle.message("sandbox.text.field.with.history.label.empty")) {
          cell(createField(emptyList(), ""))
        }
        row(DevkitUiDslBundle.message("sandbox.text.field.with.history.label.disabled")) {
          cell(createField(RECENT_URLS, RECENT_URLS[0]).apply { isEnabled = false })
        }
      }
    }
  }

  private fun createField(history: List<@NonNls String>, text: @NonNls String): TextFieldWithHistory {
    return TextFieldWithHistory().apply {
      setHistory(history)
      setText(text)
      columns(COLUMNS_MEDIUM)
    }
  }
}
