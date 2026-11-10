// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

private const val SAMPLE_PATH: @NonNls String = "src/main/resources"
private const val SAMPLE_QUERY: @NonNls String = "ActionToolbar"

internal class ExtendableTextFieldPanel : UISandboxPanel {

  override val title: String = "ExtendableTextField"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.extendable.text.field.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.extendable.text.field.group.extensions")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.extendable.text.field.extensions.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.extendable.text.field.label.several")) {
          extendableTextField()
            .columns(COLUMNS_MEDIUM)
            .text(SAMPLE_PATH)
            .applyToComponent {
              addResetExtension(this)
              addBrowseExtension({}, disposable)
            }
            .comment(DevkitUiDslBundle.message("sandbox.extendable.text.field.comment.several"))
        }
        row(DevkitUiDslBundle.message("sandbox.extendable.text.field.label.clear")) {
          extendableTextField()
            .columns(COLUMNS_MEDIUM)
            .text(SAMPLE_QUERY)
            .applyToComponent { addClearExtension(this) }
        }
        row(DevkitUiDslBundle.message("sandbox.extendable.text.field.label.disabled")) {
          extendableTextField()
            .columns(COLUMNS_MEDIUM)
            .text(SAMPLE_PATH)
            .enabled(false)
            .applyToComponent { addBrowseExtension({}, disposable) }
        }
      }
    }
  }

  private fun addResetExtension(field: ExtendableTextField) {
    field.addExtension(ExtendableTextComponent.Extension.create(
      AllIcons.Actions.Refresh,
      DevkitUiDslBundle.message("sandbox.extendable.text.field.tooltip.reset"),
    ) { field.text = SAMPLE_PATH })
  }

  private fun addClearExtension(field: ExtendableTextField) {
    field.addExtension(ExtendableTextComponent.Extension.create(
      AllIcons.Actions.Close,
      AllIcons.Actions.CloseHovered,
      DevkitUiDslBundle.message("sandbox.extendable.text.field.tooltip.clear"),
    ) { field.text = "" })
  }
}
