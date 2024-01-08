// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentsTestAction

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class ThreeStateCheckBoxPanel {

  val panel: DialogPanel = panel {
    group("States (Check Focused Manually)") {
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
