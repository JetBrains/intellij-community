// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.windsurf

import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.providers.vscode.VSCodeSettingsProcessor
import com.intellij.ide.startup.importSettings.providers.vscode.VSCodeTransferSettingsProvider
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vscode.mappings.PluginMappings
import kotlinx.coroutines.CoroutineScope

/**
 * @author Alexander Lobas
 */
class WindsurfTransferSettingsProvider(scope: CoroutineScope) : VSCodeTransferSettingsProvider(scope) {
  override val transferableIdeId: TransferableIdeId = TransferableIdeId.Windsurf

  override val name: String = "Windsurf"

  override val id: String = "Windsurf"

  override val processor: VSCodeSettingsProcessor = object : VSCodeSettingsProcessor(scope, "Windsurf", ".windsurf") {
    override fun getProcessedSettings(): Settings {
      val settings = super.getProcessedSettings()
      PluginMappings.vsCodeAiMapping(settings)
      return settings
    }
  }
}