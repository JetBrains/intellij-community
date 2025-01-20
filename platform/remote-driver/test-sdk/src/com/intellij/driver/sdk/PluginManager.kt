package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

fun Driver.getPlugin(id: String) = utility(PluginManagerCore::class).getPlugin(utility(PluginId::class).getId(id))

fun Driver.getEnabledPluginsIds() = utility(PluginManagerCore::class).getLoadedPlugins()

@Remote("com.intellij.openapi.extensions.PluginId")
interface PluginId {
  fun getId(id: String): PluginId
  fun getIdString(): String
}

@Remote("com.intellij.ide.plugins.PluginManagerCore")
interface PluginManagerCore {
  fun getPlugin(pluginId: PluginId): PluginDescriptor?
  fun getLoadedPlugins(): Array<PluginDescriptor>
}

@Remote("com.intellij.openapi.extensions.PluginDescriptor")
interface PluginDescriptor {
  fun getPluginId(): PluginId
  fun isBundled(): Boolean
  fun isEnabled(): Boolean
  fun getName(): String
}
