// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.layout.*

class ConfigurableBuilderHelper {
  companion object {
    @JvmStatic
    internal fun RowBuilder.buildFieldsPanel(@NlsContexts.BorderTitle title: String?, fields: List<ConfigurableBuilder.BeanField<*, *>>) {
      if (title != null) {
        titledRow(title) {
          appendFields(fields)
        }
      }
      else {
        appendFields(fields)
      }
    }

    private fun RowBuilder.appendFields(fields: List<ConfigurableBuilder.BeanField<*, *>>) {
      for (field in fields) {
        row {
          component(field.component)
            .onApply { field.apply() }
            .onIsModified { field.isModified() }
            .onReset { field.reset() }
        }
      }
    }
  }
}