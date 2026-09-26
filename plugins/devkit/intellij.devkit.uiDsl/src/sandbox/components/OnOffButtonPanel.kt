// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.getStateText
import com.intellij.openapi.Disposable
import com.intellij.ui.components.OnOffButton
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class OnOffButtonPanel : UISandboxPanel {

  override val title: String = "OnOffButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group(DevkitUiDslBundle.message("sandbox.states")) {
        for (isEnabled in listOf(true, false)) {
          for (isSelected in listOf(false, true)) {
            row {
              val toggle = cell(OnOffButton()).applyToComponent {
                this.isSelected = isSelected
                this.isEnabled = isEnabled
              }
              label(getStateText(toggle.component)).enabled(isEnabled)
            }
          }
        }

        // Only the enabled states: a disabled toggle is not focusable, so it has no focused asset.
        // Focus is forced so every state is comparable at a glance, the way ButtonTypesPanel does it —
        // the toggles above still show the real thing when tabbed to.
        for (isSelected in listOf(false, true)) {
          row {
            val toggle = cell(object : OnOffButton() {
              override fun hasFocus(): Boolean = true
            }).applyToComponent {
              this.isSelected = isSelected
            }
            label(getStateText(toggle.component, DevkitUiDslBundle.message("sandbox.focused")))
          }
        }
      }
    }
  }
}