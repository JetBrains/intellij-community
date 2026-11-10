// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.columns
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private val MODULE_NAMES: List<@NonNls String> = listOf(
  "intellij.platform.core",
  "intellij.platform.ide",
  "intellij.platform.ide.impl",
  "intellij.platform.lang",
  "intellij.java.impl",
  "intellij.devkit.uiDsl",
)

internal class TextFieldWithAutoCompletionPanel : UISandboxPanel {

  override val title: String = "TextFieldWithAutoCompletion"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.text.field.with.auto.completion.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.text.field.with.auto.completion.group.default")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.text.field.with.auto.completion.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.text.field.with.auto.completion.label.empty")) {
          cell(createField(null))
        }
        row(DevkitUiDslBundle.message("sandbox.text.field.with.auto.completion.label.with.value")) {
          cell(createField(MODULE_NAMES[0]))
        }
        row(DevkitUiDslBundle.message("sandbox.text.field.with.auto.completion.label.disabled")) {
          cell(createField(MODULE_NAMES[0]).apply { isEnabled = false })
        }
      }
    }
  }

  private fun createField(text: @NonNls String?): TextFieldWithAutoCompletion<String> {
    val project = ProjectManager.getInstance().defaultProject
    return TextFieldWithAutoCompletion.create(project, MODULE_NAMES, true, text)
      .columns(COLUMNS_MEDIUM)
  }
}
