// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Panel

class ConfigurableBuilderHelper {
  companion object {
    @JvmStatic
    internal fun Panel.buildFieldsPanel(@NlsContexts.BorderTitle title: String?, fields: List<ConfigurableBuilder.BeanField<*, *>>) {
      if (title != null) {
        group(title) {
          appendFields(fields)
        }
      }
      else {
        appendFields(fields)
      }
    }

    private fun Panel.appendFields(fields: List<ConfigurableBuilder.BeanField<*, *>>) {
      for (field in fields) {
        row {
          cell(field.component)
            .onApply { field.apply() }
            .onIsModified { field.isModified }
            .onReset { field.reset() }
        }
      }
    }
  }
}
