// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class JComboBoxPanel : UISandboxPanel {

  override val title: String = "JComboBox"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      val items = (1..10).map { "Item $it" }.toList()

      row("Not editable:") {
        comboBox(items)
      }
      row("Not editable, error:") {
        comboBox(items).applyToComponent {
          putClientProperty("JComponent.outline", "error")
        }
      }
      row("Not editable, warning:") {
        comboBox(items).applyToComponent {
          putClientProperty("JComponent.outline", "warning")
        }
      }
      row("Not editable, disabled:") {
        comboBox(items).enabled(false)
      }
      row("Editable:") {
        comboBox(items).applyToComponent {
          isEditable = true
        }
      }
      row("Editable, disabled:") {
        comboBox(items)
          .enabled(false)
          .applyToComponent {
            isEditable = true
          }
      }
    }
  }
}