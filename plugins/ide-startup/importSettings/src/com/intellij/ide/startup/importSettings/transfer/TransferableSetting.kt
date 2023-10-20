// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.customize.transferSettings.models.ILookAndFeel
import com.intellij.ide.customize.transferSettings.models.Keymap
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.StartupImportIcons
import com.intellij.ide.startup.importSettings.data.BaseSetting
import javax.swing.Icon

class TransferableSetting(
  override val id: String,
  override val name: String,
  override val icon: Icon,
  override val comment: String?
) : BaseSetting {

  companion object {

    const val UI_ID = "ui"
    const val KEYMAP_ID = "keymap"
    const val PLUGINS_ID = "plugins"
    const val RECENT_PROJECTS_ID = "recentProjects"

    fun uiTheme(laf: ILookAndFeel): TransferableSetting? {
      val themeName = laf.getPreview().name
      return TransferableSetting(
        UI_ID,
        ImportSettingsBundle.message("transfer.settings.ui-theme"),
        StartupImportIcons.Icons.ColorPicker,
        themeName
      )
    }

    fun keymap(keymap: Keymap): TransferableSetting {
      return TransferableSetting(
        KEYMAP_ID,
        ImportSettingsBundle.message("transfer.settings.keymap"),
        StartupImportIcons.Icons.Keyboard,
        keymap.displayName
      )
    }

    fun plugins() = TransferableSetting(
      PLUGINS_ID,
      ImportSettingsBundle.message("transfer.settings.plugins"),
      StartupImportIcons.Icons.Plugin,
      null
    )

    fun recentProjects() = TransferableSetting(
      RECENT_PROJECTS_ID,
      ImportSettingsBundle.message("transfer.settings.recent-projects"),
      StartupImportIcons.Icons.Recent,
      null
    )
  }
}
