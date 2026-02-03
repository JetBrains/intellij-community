// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.showcase

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.util.registry.Registry

/**
 * Provides [SettingsPageOnCompose] only when the registry key is enabled.
 */
class SettingsPageOnComposeProvider : ConfigurableProvider() {
  override fun canCreateConfigurable(): Boolean = Registry.`is`("devkit.compose.example.settings.page")

  override fun createConfigurable(): Configurable? =
    if (canCreateConfigurable()) SettingsPageOnCompose() else null
}
