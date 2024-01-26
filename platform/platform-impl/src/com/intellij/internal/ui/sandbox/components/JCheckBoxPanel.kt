// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
internal class JCheckBoxPanel : UISandboxPanel {

  override val title: String = "JCheckBox"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group("States") {
        row {
          panel {
            row {
              checkBox("checkBox")
            }
            row {
              checkBox("checkBoxDisabled").enabled(false)
            }
            row {
              checkBox("checkBoxSelected").selected(true)
            }
            row {
              checkBox("checkBoxSelectedDisabled").selected(true).enabled(false)
            }
          }
        }
      }
    }
  }
}