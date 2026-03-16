// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

private const val CWM_PLUGIN_ID = "com.jetbrains.codeWithMe"
private const val CWM_RIDER_PLUGIN_ID = "intellij.rider.plugins.cwm"

internal class CwmMigration261 : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    descriptor.removePlugin(CWM_PLUGIN_ID)
    descriptor.removePluginToDownload(CWM_PLUGIN_ID)

    descriptor.removePlugin(CWM_RIDER_PLUGIN_ID)
    descriptor.removePluginToDownload(CWM_RIDER_PLUGIN_ID)
  }
}