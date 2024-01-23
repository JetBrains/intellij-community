// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.openapi.components.service
import com.intellij.util.SystemProperties
import com.jetbrains.rd.util.reactive.*
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

interface WizardProvider {
  companion object {
    fun getInstance(): WizardProvider = service()
  }

  fun getWizardService(): WizardService? = null
}

class WizardProviderImpl : WizardProvider {
  private val shouldUseMockData = SystemProperties.getBooleanProperty("intellij.startup.wizard.use-mock-data", false)

  override fun getWizardService(): WizardService? {
    return if (shouldUseMockData) WizardServiceTest() else null
  }
}

interface WizardService {
  fun getKeymapService(): KeymapService

  fun getThemeService(): ThemeService

  fun getPluginService(): PluginService
}

interface ThemeService {
  enum class Theme(val themeName: @Nls String, val isDark: Boolean) {
    Dark(ImportSettingsBundle.message("theme.page.dark"), true),
    Light(ImportSettingsBundle.message("theme.page.light"),false)
  }
  companion object {
    private val themes = listOf(Theme.Dark, Theme.Light)
  }

  val themeList: List<Theme>
    get() = themes

  var currentTheme: Theme

  val schemesList: List<WizardScheme>

  fun finish(schemeId: String, theme: Theme)

  fun updateScheme(schemeId: String)
}

data class WizardScheme (
  val id: String,
  val name: @Nls String,
  val icon: Icon,
  val backgroundColor: Color
  )

interface PluginService {
  val plugins: List<WizardPlugin>
  fun install(ids: List<String>): PluginImportProgress
  fun skipPlugins()
}

interface PluginImportProgress : ImportProgress {
  val icon: IPropertyView<Icon>
}

interface WizardPlugin {
  val id: String
  val icon: Icon
  val name: String
  val description: String?
}

interface KeymapService {
  val keymaps: List<WizardKeymap>
  val shortcuts: List<Shortcut>
  fun chosen(id: String)
}

data class Shortcut(
  val id: String,
  val name: @Nls String
)


interface WizardKeymap {
  val id: String
  val name: String
  val description: @Nls String
  fun getShortcutValue(id: String): String
}
