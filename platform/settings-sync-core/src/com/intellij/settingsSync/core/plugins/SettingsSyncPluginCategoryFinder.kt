package com.intellij.settingsSync.core.plugins

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.components.SettingsCategory

internal object SettingsSyncPluginCategoryFinder {

  private val UI_CATEGORIES = setOf(
    "Theme",
    "Editor Color Schemes")

  private val UI_EXTENSIONS = setOf(
    "com.intellij.bundledColorScheme",
    "com.intellij.themeProvider"
  )

  fun getPluginCategory(descriptor: IdeaPluginDescriptor): SettingsCategory {
    if (UI_CATEGORIES.contains(descriptor.category) || containsOnlyUIExtensions(descriptor)) {
      return SettingsCategory.UI
    }
    return SettingsCategory.PLUGINS
  }

  private fun containsOnlyUIExtensions(descriptor: IdeaPluginDescriptor) : Boolean {
    if (descriptor is IdeaPluginDescriptorImpl) {
      return descriptor.extensions?.all {
        UI_EXTENSIONS.contains(it.key)
      } ?: false
    }
    return false
  }
}