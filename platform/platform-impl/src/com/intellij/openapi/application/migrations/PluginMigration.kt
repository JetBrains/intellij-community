// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginNode
import com.intellij.openapi.application.PluginMigrationOptions
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
abstract class PluginMigration {
  open fun migratePlugins(options: PluginMigrationOptions) {
    migratePlugins(PluginMigrationDescriptor(options))
  }

  abstract fun migratePlugins(descriptor: PluginMigrationDescriptor)


  class PluginMigrationDescriptor(val options: PluginMigrationOptions) {
    val currentPluginsToDownload by lazy { getPluginIDs(options.pluginsToDownload) }
    val currentPluginsToMigrate by lazy { getPluginIDs(options.pluginsToMigrate) }

    private fun getPluginIDs(plugins: Collection<IdeaPluginDescriptor>) = plugins.map { it.pluginId.idString }.toSet()

    fun addPluginIfNeeded(pluginIdString: String) {
      if (!currentPluginsToDownload.contains(pluginIdString)) {
        options.pluginsToDownload.add(PluginNode(PluginId.getId(pluginIdString)))
      }
    }

    fun removePlugin(pluginIdString: String) {
      options.pluginsToMigrate.removeIf { it.pluginId.idString == pluginIdString }
    }

    fun removePluginToDownload(pluginIdString: String) {
      options.pluginsToDownload.removeIf { it.pluginId.idString == pluginIdString }
    }
  }
}