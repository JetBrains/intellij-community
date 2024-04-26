// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.withStateLabel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class JTextFieldPanel : UISandboxPanel {

  override val title: String = "JBTextField"

  override fun createContent(disposable: Disposable): JComponent {
    val result = panel {
      withStateLabel {
        textField()
      }
      withStateLabel {
        textField().applyToComponent {
          isEditable = false
        }
      }
      withStateLabel {
        textField().enabled(false)
      }
      row("With empty text:") {
        textField().apply {
          component.emptyText.text = "Type some text here"
        }
      }

      group("Validation") {
        withStateLabel("Error") {
          textField().validationOnInput {
            validate(it, true)
          }.validationOnApply {
            validate(it, true)
          }
        }
        withStateLabel("Warning") {
          textField().validationOnInput {
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

  private fun validate(textField: JBTextField, isError: Boolean): ValidationInfo? {
    if (textField.text.isNullOrBlank()) {
      return if (isError) {
        ValidationInfo("Text must not be empty")
      }
      else {
        ValidationInfo("Text should not be empty").asWarning()
      }
    }

    return null
  }

}