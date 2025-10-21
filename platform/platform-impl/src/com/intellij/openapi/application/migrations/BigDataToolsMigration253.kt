// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

private const val BDT_CORE_PLUGIN_ID = "com.intellij.bigdatatools.core"
private const val BDT_CORE_UI_PLUGIN_ID = "intellij.bigdatatools.coreUi"
private const val BDT_AWS_BASE_PLUGIN_ID = "intellij.bigdatatools.awsBase"

private const val BDT_RFS_PLUGIN_ID = "com.intellij.bigdatatools.rfs"
private const val BDT_GCLOUD_PLUGIN_ID = "intellij.bigdatatools.gcloud"
private const val BDT_AZURE_PLUGIN_ID = "intellij.bigdatatools.azure"

private val BDT_NEW_DEPENDENCIES = mapOf(
  BDT_CORE_PLUGIN_ID to listOf(BDT_CORE_UI_PLUGIN_ID, BDT_AWS_BASE_PLUGIN_ID),
  BDT_RFS_PLUGIN_ID to listOf(BDT_GCLOUD_PLUGIN_ID, BDT_AZURE_PLUGIN_ID)
)

internal class BigDataToolsMigration253: PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    BDT_NEW_DEPENDENCIES.forEach { (pluginId, newPluginDependencies) ->
      if (descriptor.currentPluginsToDownload.contains(pluginId) ||
          descriptor.currentPluginsToMigrate.contains(pluginId)) {
        newPluginDependencies.forEach { descriptor.addPluginIfNeeded(it) }
      }
    }
  }
}