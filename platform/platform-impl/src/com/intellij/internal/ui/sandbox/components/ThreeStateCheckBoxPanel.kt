// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class ThreeStateCheckBoxPanel : UISandboxPanel {

  override val title: String = "ThreeStateCheckBoxPanel"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group("States") {
        row {
          panel {
            row {
              threeStateCheckBox("checkBoxIndeterminate")
            }
            row {
              threeStateCheckBox("checkBoxIndeterminateDisabled").enabled(false)
            }
          }
        }
      }
    }
  }
}