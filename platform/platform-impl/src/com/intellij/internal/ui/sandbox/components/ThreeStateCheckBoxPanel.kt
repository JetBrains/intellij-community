// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.sandbox.components

import com.intellij.internal.ui.sandbox.UISandboxPanel
import com.intellij.internal.ui.sandbox.applyStateText
import com.intellij.openapi.Disposable
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.ThreeStateCheckBox
import javax.swing.JComponent

internal class ThreeStateCheckBoxPanel : UISandboxPanel {

  override val title: String = "ThreeStateCheckBoxPanel"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      group("States") {
        for (isEnabled in listOf(true, false)) {
          for (state in listOf(ThreeStateCheckBox.State.NOT_SELECTED, ThreeStateCheckBox.State.DONT_CARE, ThreeStateCheckBox.State.SELECTED)) {
            row {
              threeStateCheckBox("").applyToComponent {
                this.state = state
              }.enabled(isEnabled)
                .applyStateText()
            }
          }
        }
      }
    }
  }
}