package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

fun Driver.getPlugin(id: String) = utility(PluginManagerCoreRef::class).getPlugin(utility(PluginIdRef::class).getId(id))

@Remote("com.intellij.openapi.extensions.PluginId")
interface PluginIdRef {
  fun getId(id: String): PluginIdRef
}

@Remote("com.intellij.ide.plugins.PluginManagerCore")
interface PluginManagerCoreRef {
  fun getPlugin(pluginId: PluginIdRef): PluginDescriptorRef
}

@Remote("com.intellij.openapi.extensions.PluginDescriptor")
interface PluginDescriptorRef {
  fun isEnabled(): Boolean
}