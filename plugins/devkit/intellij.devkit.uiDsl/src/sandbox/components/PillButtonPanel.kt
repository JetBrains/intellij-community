// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.PillButton
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class PillButtonPanel : UISandboxPanel {

  override val title: String = "PillButton"

  override val isInternalApi: Boolean
    get() = true

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group(DevkitUiDslBundle.message("sandbox.pill.button.predefined.colors")) {
        row {
          cell(PillButton(DevkitUiDslBundle.message("sandbox.pill.button.color.blue")))
        }
        row {
          cell(PillButton(DevkitUiDslBundle.message("sandbox.pill.button.color.blue")))
        }.enabled(false)
      }
    }
  }
}