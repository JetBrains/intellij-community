// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.withStateLabel
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class JComboBoxPanel : UISandboxPanel {

  override val title: String = "JComboBox"

  override fun createContent(disposable: Disposable): JComponent {
    val items = (1..10).map { "Item $it" }.toList()

    val result = panel {
      withStateLabel {
        comboBox(items)
      }
      withStateLabel {
        comboBox(items).enabled(false)
      }
      withStateLabel {
        comboBox(items).applyToComponent {
          isEditable = true
        }
      }
      withStateLabel {
        comboBox(items)
          .enabled(false)
          .applyToComponent {
            isEditable = true
          }
      }

      group("Validation") {
        withStateLabel("Error") {
          comboBox(items).validationOnInput {
            validate(it, true)
          }.validationOnApply {
            validate(it, true)
          }
        }

        withStateLabel("Warning") {
          comboBox(items).validationOnInput {
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

  private fun validate(comboBox: ComboBox<*>, isError: Boolean): ValidationInfo? {
    if (comboBox.selectedItem == "Item 2") {
      return null
    }

    return if (isError) {
      ValidationInfo("Item 2 must be selected")
    }
    else {
      ValidationInfo("Item 2 should be selected").asWarning()
    }
  }
}