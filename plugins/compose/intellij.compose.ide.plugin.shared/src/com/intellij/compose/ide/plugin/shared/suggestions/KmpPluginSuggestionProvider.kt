// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.suggestions

import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.buildSuggestionIfNeeded
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformProject
import org.jetbrains.kotlin.idea.util.isKotlinFileType

private const val KMP_PLUGIN_ID: String = "com.jetbrains.kmm"
private const val KMP_PLUGIN_NAME: String = "Kotlin Multiplatform"
private const val KMP_PLUGIN_SUGGESTION_DISMISSED_KEY: String = "compose.kmp.plugin.suggestion.dismissed"

/**
 * Advertises the Kotlin Multiplatform plugin when a Kotlin or Swift file is opened in a Kotlin Multiplatform
 * project and the plugin is not installed yet.
 */
internal class KmpPluginSuggestionProvider : PluginSuggestionProvider {
  override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
    if (!file.isKotlinFileType() && !file.isSwiftFile()) return null
    if (!project.isMultiPlatformProject) return null

    return buildSuggestionIfNeeded(
      project,
      pluginIds = listOf(KMP_PLUGIN_ID),
      pluginName = KMP_PLUGIN_NAME,
      suggestionText = ComposeIdeBundle.message("compose.kmp.plugin.suggestion.text"),
      suggestionDismissKey = KMP_PLUGIN_SUGGESTION_DISMISSED_KEY,
    )
  }

  private fun VirtualFile.isSwiftFile(): Boolean = extension == "swift"
}
