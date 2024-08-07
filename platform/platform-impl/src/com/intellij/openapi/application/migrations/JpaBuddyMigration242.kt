// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

import com.intellij.ide.plugins.PluginManagerCore

private const val JPAB_PLUGIN_ID = "com.haulmont.jpab"
private const val JPA_MODEL_PLUGIN_ID = "com.intellij.jpa.jpb.model"

internal class JpaBuddyMigration242 : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    if (PluginManagerCore.buildNumber.productCode == "IC") {
      if (descriptor.currentPluginsToMigrate.contains(JPAB_PLUGIN_ID)) {
        descriptor.addPluginIfNeeded(JPA_MODEL_PLUGIN_ID)
      }
    }
  }
}