// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class OnOffButtonPanel : UISandboxPanel {

  override val title: String = "OnOffButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row(DevkitUiDslBundle.message("sandbox.default.text")) {
        onOffButton(true)
        onOffButton(false)
      }
      row(DevkitUiDslBundle.message("sandbox.no.text")) {
        onOffButton(true).applyToComponent {
          onText = null
          offText = null
        }
        onOffButton(false).applyToComponent {
          onText = null
          offText = null
        }
      }
    }
  }

  private fun Row.onOffButton(isOn: Boolean): Cell<OnOffButton> {
    return cell(OnOffButton()).applyToComponent {
      isSelected = isOn
    }
  }
}