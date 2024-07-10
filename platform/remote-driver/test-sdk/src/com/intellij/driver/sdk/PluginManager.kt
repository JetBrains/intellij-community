package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

fun Driver.getPlugin(id: String) = utility(PluginManagerCore::class).getPlugin(utility(PluginId::class).getId(id))

@Remote("com.intellij.openapi.extensions.PluginId")
interface PluginId {
  fun getId(id: String): PluginId
}

@Remote("com.intellij.ide.plugins.PluginManagerCore")
interface PluginManagerCore {
  fun getPlugin(pluginId: PluginId): PluginDescriptor
}

@Remote("com.intellij.openapi.extensions.PluginDescriptor")
interface PluginDescriptor {
  fun isEnabled(): Boolean
}