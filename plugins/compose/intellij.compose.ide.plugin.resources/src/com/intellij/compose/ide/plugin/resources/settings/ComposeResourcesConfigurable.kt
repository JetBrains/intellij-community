// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.settings

import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.options.BoundCompositeSearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel

/**
 * The general **Compose Resources** settings page.
 *
 * It renders as a single page that contains a section per [ComposeResourcesSettingsContributor],
 * letting individual features contribute their own settings.
 */
internal class ComposeResourcesConfigurable : BoundCompositeSearchableConfigurable<UnnamedConfigurable>(
  displayName = ComposeIdeBundle.message("compose.resources.title"),
  helpTopic = ID,
  _id = ID,
) {
  private val contributors: List<ComposeResourcesSettingsContributor>
    get() = ComposeResourcesSettingsContributor.EP_NAME.extensionList

  override fun createConfigurables(): List<UnnamedConfigurable> =
    contributors.map { it.createConfigurable() }

  override fun createPanel(): DialogPanel = panel {
    contributors.zip(configurables) { contributor, configurable ->
      group(contributor.displayName) {
        appendDslConfigurable(configurable)
      }
    }
  }

  companion object {
    const val ID: String = "compose.resources.settings"
  }
}
