// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.advanced

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UiDslConfigurable
import com.intellij.ui.layout.*
import javax.swing.JComponent

class AdvancedSettingsConfigurable : UiDslConfigurable.Simple(), SearchableConfigurable {
  override fun RowBuilder.createComponentRow() {
    val groupedExtensions = AdvancedSettingBean.EP_NAME.extensions.groupBy {
      it.group() ?: ApplicationBundle.message("group.advanced.settings.other")
    }

    for ((group, extensions) in groupedExtensions) {
      titledRow(group) {
        for (extension in extensions) {
          row {
            control(extension).also {
              extension.description()?.let { description -> it.comment(description) }
            }
          }
        }
      }
    }
  }

  private fun Row.control(extension: AdvancedSettingBean): CellBuilder<JComponent> {
    return when(extension.type()) {
      AdvancedSettingType.Bool ->
        checkBox(
          extension.title(),
          { AdvancedSettings.getBoolean(extension.id) },
          { AdvancedSettings.setBoolean(extension.id, it) }
      )

      AdvancedSettingType.Int -> {
        label(extension.title() + ":")
        intTextField(
          { AdvancedSettings.getInt(extension.id) },
          { AdvancedSettings.setInt(extension.id, it) },
          columns = 10
        )
      }
    }
  }

  private fun applyFilter(text: String?) {

  }

  override fun getDisplayName(): String = ApplicationBundle.message("title.advanced.settings")

  override fun getId(): String = "advanced.settings"

  override fun enableSearch(option: String?): Runnable {
    return Runnable { applyFilter(option) }
  }
}
