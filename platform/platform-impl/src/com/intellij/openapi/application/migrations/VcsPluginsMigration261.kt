// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

private const val PERFORCE_PLUGIN_ID = "PerforceDirectPlugin"
private const val MERCURIAL_PLUGIN_ID = "hg4idea"
private const val SUBVERSION_PLUGIN_ID = "Subversion"

private val UNBUNDLED_VCS_PLUGINS = listOf(PERFORCE_PLUGIN_ID, MERCURIAL_PLUGIN_ID, SUBVERSION_PLUGIN_ID)

internal class VcsPluginsMigration261 : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    for (pluginId in UNBUNDLED_VCS_PLUGINS) {
      descriptor.addPluginIfNeeded(pluginId)
    }
  }
}
