// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui.componentsTestAction

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class JSpinnerPanel {

  val panel: DialogPanel = panel {
    group("JSpinner") {
      row("Enabled:") {
        spinner(0.0..10.0)
      }
      row("Disabled:") {
        spinner(0.0..10.0)
          .enabled(false)
      }
    }
    group("JBIntSpinner") {
      row("Enabled:") {
        spinner(0..10)
      }
      row("Disabled:") {
        spinner(0..10)
          .enabled(false)
      }
    }
  }
}
