// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.data

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.openapi.components.service
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.IVoidSource
import com.jetbrains.rd.util.reactive.Signal
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.Icon

interface StartupWizardService {
  companion object {
    fun getInstance(): StartupWizardService? {
      val service =
        if (useMockDataForStartupWizard) WizardServiceTest()
        else service<StartupWizardService>()
      return if (service.isActive) service else null
    }
  }

  val isActive: Boolean

  val shouldClose: IVoidSource

  fun getKeymapService(): KeymapService

  fun getThemeService(): ThemeService

  fun getPluginService(): PluginService

  /**
   * Gets called when the wizard is shows on the screen.
   */
  fun onEnter()

  /**
   * Gets called when the wizard is canceled.
   */
  fun onCancel()

  /**
   * Gets called when the wizard is closed (even if canceled).
   */
  fun onExit()
}

class DisabledStartupWizardPages : StartupWizardService {
  override val isActive = false
  override val shouldClose = Signal<Unit>()
  override fun getKeymapService() = error("Startup wizard is disabled.")
  override fun getThemeService() = error("Startup wizard is disabled.")
  override fun getPluginService() = error("Startup wizard is disabled.")
  override fun onEnter() {}
  override fun onCancel() {}
  override fun onExit() {}
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

  fun onStepEnter(isForwardDirection: Boolean)
  fun updateScheme(schemeId: String)
}

data class WizardScheme(
  val id: String,
  val name: @Nls String,
  val icon: Icon,
  val backgroundColor: Color
  )

interface PluginService {
  val plugins: List<WizardPlugin>
  fun onStepEnter()
  fun install(lifetime: Lifetime, ids: List<String>): PluginImportProgress
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
  fun onStepEnter(isForwardDirection: Boolean)
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
