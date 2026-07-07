// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.settings

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.UnnamedConfigurable
import org.jetbrains.annotations.Nls

/**
 * Contributes a section to the general **Compose Resources** settings page
 * ([ComposeResourcesConfigurable]).
 *
 * Each contributed [UnnamedConfigurable] is rendered under its own group titled with [displayName],
 * so features can add their own settings without modifying the page.
 */
internal interface ComposeResourcesSettingsContributor {

  @get:Nls
  val displayName: String

  fun createConfigurable(): UnnamedConfigurable

  companion object {
    val EP_NAME = ExtensionPointName.create<ComposeResourcesSettingsContributor>(
      "com.intellij.compose.ide.plugin.resources.settingsContributor"
    )
  }
}
