// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.features.FileHandlerFeatureDetector

internal sealed interface PluginAdvertiserSuggestion

internal data class PluginAdvertisedByFileName(
  // Either extension or file name.
  // Depends on which of the two properties has higher priority for advertising plugins for this specific file.
  @JvmField val extensionOrFileName: String,
  @JvmField val plugins: Set<PluginData> = emptySet(),
) : PluginAdvertiserSuggestion

internal data class PluginAdvertisedByFileContent(
  @JvmField val fileHandler: FileHandlerFeatureDetector,
  @JvmField val plugins: Set<PluginData> = emptySet(),
) : PluginAdvertiserSuggestion

internal object NoSuggestions : PluginAdvertiserSuggestion {
  override fun toString(): String = "<No Suggested Plugins>"
}