package com.intellij.settingsSync.core.plugins

import com.intellij.openapi.extensions.PluginId

interface SettingsSyncPluginInstaller {
  suspend fun installPlugins(pluginsToInstall: List<PluginId>)
}