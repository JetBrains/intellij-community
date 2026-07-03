// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.initWithText
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import com.intellij.ui.dsl.builder.text
import javax.swing.JComponent

internal class JBTextAreaPanel : UISandboxPanel {

  override val title: String = "JBTextArea"

  override fun createContent(disposable: Disposable): JComponent {
    val result = panel {
      row {
        textArea()
          .label(DevkitUiDslBundle.message("sandbox.label.enabled"), LabelPosition.TOP)
          .initWithText()
      }
      row {
        textArea()
          .label(DevkitUiDslBundle.message("sandbox.label.disabled"), LabelPosition.TOP)
          .enabled(false)
          .initWithText()
      }
      row {
        textArea()
          .label(DevkitUiDslBundle.message("sandbox.label.empty.text"), LabelPosition.TOP)
          .align(AlignX.FILL)
          .rows(3)
          .apply {
            component.emptyText.text = "Type some text here"
          }
      }
      row {
        cell(JBTextArea())
          .label(DevkitUiDslBundle.message("sandbox.label.jbtextarea.without.scroll"), LabelPosition.TOP)
          .text("Some text\nNew line")
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.validation")) {
        row {
          textArea()
            .label(DevkitUiDslBundle.message("sandbox.label.error"), LabelPosition.TOP)
            .initWithText()
            .validationOnInput {
              validate(it, true)
            }.validationOnApply {
              validate(it, true)
            }
        }
        row {
          textArea()
            .label(DevkitUiDslBundle.message("sandbox.label.warning"), LabelPosition.TOP)
            .initWithText()
            .validationOnInput {
              validate(it, false)
            }.validationOnApply {
              validate(it, false)
            }
        }
      }
    }

    result.registerValidators(disposable)
    result.validateAll()

    return result
  }

  private fun validate(textArea: JBTextArea, isError: Boolean): ValidationInfo? {
    if (!textArea.text.isNullOrBlank()) {
      return if (isError) {
        ValidationInfo(DevkitUiDslBundle.message("sandbox.dialog.message.text.must.be.empty"))
      }
      else {
        ValidationInfo(DevkitUiDslBundle.message("sandbox.dialog.message.text.should.be.empty")).asWarning()
      }
    }

    return null
  }
}
