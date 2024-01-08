// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentsTestAction

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class ComboBoxPanel {

  val panel: DialogPanel = panel {
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
