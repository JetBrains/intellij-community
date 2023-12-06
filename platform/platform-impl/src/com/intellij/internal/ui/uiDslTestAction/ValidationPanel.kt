// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.uiDslTestAction

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.layout.selected
import org.jetbrains.annotations.ApiStatus
import javax.swing.JCheckBox

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class ValidationPanel(parentDisposable: Disposable) {

  lateinit var panel: DialogPanel

  init {
    panel = panel {
      lateinit var cbValidationEnabled: JCheckBox

      row {
        cbValidationEnabled = checkBox("Validation enabled")
          .selected(true)
          .component
      }

      row {
        textField()
          .comment("Must be not empty")
          .cellValidation {
            enabledIf(cbValidationEnabled.selected)
            addApplyRule("Must be not empty") { it.text.isNullOrEmpty() }
          }
      }

      row("Segmented Button:") {
        val segmentedButton = segmentedButton((1..4).toList()) { text = "Item $it" }
          .validation {
            enabledIf(cbValidationEnabled.selected)
            addApplyRule("Cannot be empty") { it.selectedItem == null }
          }
        button("Reset") {
          segmentedButton.selectedItem = null
        }
      }

      row {
        button("Validate") {
          panel.validateAll()
        }
      }
    }

    panel.registerValidators(parentDisposable)
  }
}