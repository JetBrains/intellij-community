// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.SearchTextField
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class JTextFieldPanel : UISandboxPanel {

  override val title: String = "JTextField"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row("Editable:") {
        textField()
      }
      row("Editable, error:") {
        textField().applyToComponent {
          putClientProperty("JComponent.outline", "error")
        }
      }
      row("Editable, warning:") {
        textField().applyToComponent {
          putClientProperty("JComponent.outline", "warning")
        }
      }
      row("Not editable:") {
        textField().applyToComponent {
          isEditable = false
        }
      }
      row("Disabled:") {
        textField().enabled(false)
      }

      group("SearchTextField") {
        row("Editable:") {
          cell(SearchTextField())
        }
      }
    }
  }
}