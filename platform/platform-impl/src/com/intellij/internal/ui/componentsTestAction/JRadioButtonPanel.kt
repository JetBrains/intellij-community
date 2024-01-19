// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentsTestAction

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import org.jetbrains.annotations.ApiStatus

@Suppress("DialogTitleCapitalization")
@ApiStatus.Internal
internal class JRadioButtonPanel {

  val panel: DialogPanel = panel {
    group("States (Check Focused Manually)") {
      buttonsGroup("Enabled:") {
        row {
          radioButton("radioButton")
            .comment("To focus radioButton without selection press left mouse button, move mouse outside of the radioButton and release the button")
        }
        row {
          radioButton("radioButtonSelected").selected(true)
        }
      }
    }
    buttonsGroup("Disabled:") {
      row {
        radioButton("radioButtonDisabled").enabled(false)
      }
      row {
        radioButton("radioButtonSelectedDisabled").selected(true).enabled(false)
      }
    }
  }
}
