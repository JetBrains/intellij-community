// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.applyStateText
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.JComponent

internal class JCheckBoxPanel : UISandboxPanel {

  override val title: String = "JCheckBox"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group("States") {
        for (isEnabled in listOf(true, false)) {
          for (isSelected in listOf(false, true)) {
            row {
              checkBox("")
                .selected(isSelected)
                .enabled(isEnabled)
                .applyStateText()
            }
          }
        }
      }
    }
  }
}