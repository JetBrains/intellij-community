// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.ide.DataManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import java.awt.Component

internal fun createConfigurablesListPanel(description: String?,
                                          configurables: List<Configurable>,
                                          configurableEditor: ConfigurableEditor?): DialogPanel {
  return panel {
    description?.let {
      row {
        label(description)
          .customize(UnscaledGaps(bottom = 11))
      }
    }

    indent {
      for (configurable in configurables) {
        row {
          link(configurable.displayName) { event ->
            if (configurableEditor == null) {
              val component = event.source as? Component
              val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(component))
              settings?.select(configurable)
            }
            else {
              configurableEditor.openLink(configurable)
            }
          }.customize(UnscaledGaps(bottom = 4))
        }
      }
    }
  }
}
