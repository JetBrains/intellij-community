// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.cursor

import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.providers.vscode.VSCodeSettingsProcessor
import com.intellij.ide.startup.importSettings.providers.vscode.VSCodeTransferSettingsProvider
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vscode.mappings.PluginMappings
import kotlinx.coroutines.CoroutineScope

/**
 * @author Alexander Lobas
 */
class CursorTransferSettingsProvider(scope: CoroutineScope) : VSCodeTransferSettingsProvider(scope) {
  override val transferableIdeId: TransferableIdeId = TransferableIdeId.Cursor

  override val name: String = "Cursor"

  override val id: String = "Cursor"

  override val processor: VSCodeSettingsProcessor = object : VSCodeSettingsProcessor(scope, "Cursor", ".cursor") {
    override fun getProcessedSettings(): Settings {
      val settings = super.getProcessedSettings()
      PluginMappings.vsCodeAiMapping(settings)
      return settings
    }
  }
}