// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private const val PROGRAM_ARGUMENTS: @NonNls String = "--config \"config/sample settings.yaml\" --verbose"

internal class RawCommandLineEditorPanel : UISandboxPanel {

  override val title: String = "RawCommandLineEditor"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.raw.command.line.editor.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.raw.command.line.editor.group.default")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.raw.command.line.editor.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.raw.command.line.editor.label.arguments")) {
          cell(createEditor(PROGRAM_ARGUMENTS))
        }
        row(DevkitUiDslBundle.message("sandbox.raw.command.line.editor.label.empty")) {
          cell(createEditor(""))
        }
        row(DevkitUiDslBundle.message("sandbox.raw.command.line.editor.label.disabled")) {
          cell(createEditor(PROGRAM_ARGUMENTS).apply { isEnabled = false })
        }
      }
    }
  }

  private fun createEditor(text: @NonNls String): RawCommandLineEditor {
    return RawCommandLineEditor().apply {
      setText(text)
      editorField.columns(COLUMNS_MEDIUM)
    }
  }
}
