// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentsTestAction

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class JTextFieldPanel {

  val panel: DialogPanel = panel {
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
    row("Disabled:") {
      textField().enabled(false)
    }
  }
}
