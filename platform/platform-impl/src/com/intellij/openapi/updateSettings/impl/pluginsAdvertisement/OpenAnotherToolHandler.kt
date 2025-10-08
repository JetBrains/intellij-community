// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface OpenAnotherToolHandler {

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<OpenAnotherToolHandler> = create("com.intellij.openAnotherToolHandler")
  }

  fun isApplicable(project: Project?, suggestedIde: SuggestedIde, pluginId: PluginId?): Boolean

  fun openTool(project: Project?, suggestedIde: SuggestedIde, pluginId: PluginId? = null, pathToOpen: Path? = null)

}