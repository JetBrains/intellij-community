// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.data.BaseSetting
import javax.swing.Icon

class TransferableSetting(
  override val id: String,
  override val name: String,
  override val icon: Icon
) : BaseSetting {

  override val comment: String? = null

  companion object {

    fun laf() = TransferableSetting(
      "laf",
      ImportSettingsBundle.message("transfer.settings.look-and-feel"),
      AllIcons.Actions.Stub // TODO: Choose the right icon
    )

    fun syntaxScheme() = TransferableSetting(
      "syntaxScheme",
      ImportSettingsBundle.message("transfer.settings.syntax-scheme"),
      AllIcons.Actions.Stub // TODO: Choose the right icon
    )

    fun keymap() = TransferableSetting(
      "keymap",
      ImportSettingsBundle.message("transfer.settings.keymap"),
      AllIcons.Actions.Stub // TODO: Choose the right icon
    )

    fun plugins() = TransferableSetting(
      "plugins",
      ImportSettingsBundle.message("transfer.settings.plugins"),
      AllIcons.Actions.Stub // TODO: Choose the right icon
    )

    fun recentProjects() = TransferableSetting(
      "recentProjects",
      ImportSettingsBundle.message("transfer.settings.recent-projects"),
      AllIcons.Actions.Stub // TODO: Choose the right icon
    )
  }
}
