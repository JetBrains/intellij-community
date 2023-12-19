// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.openapi.components.service
import com.jetbrains.rd.util.reactive.IOptPropertyView
import com.jetbrains.rd.util.reactive.IPropertyView
import org.jetbrains.annotations.Nls
import javax.swing.Icon

interface WizardProvider {
  companion object {
    fun getInstance(): WizardProvider = service()
  }

  fun getWizardService(): WizardService? = null
}

interface WizardService {
  fun getKeymapService(): KeymapService

  fun getThemeService(): ThemeService

  fun getPluginService(): PluginService
}

interface ThemeService {
  val themeList: List<WizardTheme>
  fun getEditorImageById(themeId: String, isDark: Boolean): Icon?

  fun chosen(themeId: String, isDark: Boolean)
}

interface WizardTheme {
  val id: String
  val name: @Nls String
}

interface PluginService {
  val plugins: List<WizardPlugin>
  fun install(ids: List<String>): PluginImportProgress
}

interface PluginImportProgress {
  val progressMessage: IPropertyView<@Nls String?>
  val progress: IOptPropertyView<Int>
}

interface WizardPlugin {
  val id: String
  val icon: Icon
  val name: String
  val description: String
}

interface KeymapService {
  val maps: List<WizardKeymap>
  fun chosen(id: String)
}

data class WizardKeymap (
  val id: String,
  val title: String,
  val description: String,
  val keymaps: List<Shortcut>
)

data class Shortcut (
  val name: String,
  val shortcut: String
)