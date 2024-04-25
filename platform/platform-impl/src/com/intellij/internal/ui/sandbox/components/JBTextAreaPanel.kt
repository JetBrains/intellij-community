// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.initWithText
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

internal class JBTextAreaPanel : UISandboxPanel {

  override val title: String = "JBTextArea"

  override fun createContent(disposable: Disposable): JComponent {
    val result = panel {
      row {
        textArea()
          .label("Enabled:", LabelPosition.TOP)
          .initWithText()
      }
      row {
        textArea()
          .label("Disabled:", LabelPosition.TOP)
          .enabled(false)
          .initWithText()
      }
      row {
        textArea()
          .label("Empty text:", LabelPosition.TOP)
          .align(AlignX.FILL)
          .rows(3)
          .apply {
            component.emptyText.text = "Type some text here"
          }
      }
      row {
        cell(JBTextArea())
          .label("JBTextArea without scroll:", LabelPosition.TOP)
          .text("Some text\nNew line")
      }

      group("Validation") {
        row {
          textArea()
            .label("Error:", LabelPosition.TOP)
            .initWithText()
            .validationOnInput {
              validate(it, true)
            }.validationOnApply {
              validate(it, true)
            }
        }
        row {
          textArea()
            .label("Warning:", LabelPosition.TOP)
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
        ValidationInfo("Text must be empty")
      }
      else {
        ValidationInfo("Text should be empty").asWarning()
      }
    }

    return null
  }
}
