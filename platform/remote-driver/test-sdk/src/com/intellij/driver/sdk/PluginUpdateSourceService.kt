package com.intellij.driver.sdk

import com.intellij.driver.client.Remote


@Remote("com.intellij.openapi.updateSettings.impl.PluginUpdateSourceId")
interface PluginUpdateSourceId {
  fun isMarketplace(): Boolean
  fun getHost(): String?
}

@Remote("com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService")
interface PluginUpdateSourceService {
  fun getPluginUpdateSourceId(pluginId: PluginId): PluginUpdateSourceId?
}