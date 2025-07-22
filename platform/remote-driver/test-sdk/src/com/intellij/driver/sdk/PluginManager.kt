package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.utility

fun Driver.getPlugin(id: String): PluginDescriptor? {
  return utility<PluginManagerCore>().getPlugin(utility(PluginId::class).getId(id))
}

fun Driver.getEnabledPlugins(): Array<PluginDescriptor> {
  return utility<PluginManagerCore>().getLoadedPlugins()
}

fun Driver.getDisabledPlugins(enabledPlugins: Set<String>): List<String> {
  val actual = getEnabledPluginsIds()
  return enabledPlugins.minus(actual).filter {
    val plugin = getPlugin(it)
    // plugin == null means removed plugins since this is not obvious.
    // plugin.getName() == "IDEA CORE" means moved/renamed plugin, but it remains for backward compatibility
    plugin != null && plugin.isBundled() && plugin.getName() != "IDEA CORE"
  }
}

fun Driver.getEnabledPluginsIds(): Set<String> {
  return getEnabledPlugins()
    .filter { it.isBundled() }
    .map { it.getPluginId().getIdString() }
    .toSet()
}

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
