// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.migrations

internal class SpaceMigration252 : PluginMigration() {
  override fun migratePlugins(descriptor: PluginMigrationDescriptor) {
    descriptor.removePlugin("com.jetbrains.space")
  }
}