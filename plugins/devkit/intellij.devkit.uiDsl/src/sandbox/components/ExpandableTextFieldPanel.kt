// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private const val VM_OPTIONS: @NonNls String = "-Xmx2048m -Dfile.encoding=UTF-8 -ea"
private const val SEMICOLON_VALUES: @NonNls String = "alpha;beta;gamma;delta"

internal class ExpandableTextFieldPanel : UISandboxPanel {

  override val title: String = "ExpandableTextField"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.expandable.text.field.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.expandable.text.field.group.default")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.expandable.text.field.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.expandable.text.field.label.default")) {
          expandableTextField()
            .columns(COLUMNS_MEDIUM)
            .text(VM_OPTIONS)
        }
        row(DevkitUiDslBundle.message("sandbox.expandable.text.field.label.disabled")) {
          expandableTextField()
            .columns(COLUMNS_MEDIUM)
            .text(VM_OPTIONS)
            .enabled(false)
        }
      }

      group(DevkitUiDslBundle.message("sandbox.expandable.text.field.group.separator")) {
        row(DevkitUiDslBundle.message("sandbox.expandable.text.field.label.semicolons")) {
          expandableTextField(ParametersListUtil.COLON_LINE_PARSER, ParametersListUtil.COLON_LINE_JOINER)
            .columns(COLUMNS_MEDIUM)
            .text(SEMICOLON_VALUES)
            .comment(DevkitUiDslBundle.message("sandbox.expandable.text.field.separator.comment"))
        }
      }
    }
  }
}
