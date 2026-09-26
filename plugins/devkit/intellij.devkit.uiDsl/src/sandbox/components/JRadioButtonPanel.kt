// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.devkit.uiDsl.sandbox.applyStateText
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.JComponent

internal class JRadioButtonPanel : UISandboxPanel {

  override val title: String = "JRadioButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group(DevkitUiDslBundle.message("sandbox.border.title.states")) {
        buttonsGroup {
          row {
            radioButton("")
              .comment(
                DevkitUiDslBundle.message("sandbox.text.to.focus.radiobutton"))
              .applyStateText()
          }
          row {
            radioButton("")
              .selected(true)
              .applyStateText()
          }
        }

        buttonsGroup {
          for (isSelected in listOf(false, true)) {
            row {
              radioButton("")
                .selected(isSelected)
                .enabled(false)
                .applyStateText()
            }
          }
        }
      }
    }
  }
}