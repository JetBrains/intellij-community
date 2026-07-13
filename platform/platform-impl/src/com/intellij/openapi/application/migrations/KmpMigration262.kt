// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

internal class KmpMigration262 : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    if (descriptor.currentPluginsToMigrate.contains(KMP_PLUGIN_ID) ||
        descriptor.currentPluginsToDownload.contains(KMP_PLUGIN_ID)) {
      descriptor.addPluginIfNeeded(KTC_PLUGIN_ID)
    }
  }
}

private const val KMP_PLUGIN_ID = "com.jetbrains.kmm"
private const val KTC_PLUGIN_ID = "org.jetbrains.kotlin-toolchain"