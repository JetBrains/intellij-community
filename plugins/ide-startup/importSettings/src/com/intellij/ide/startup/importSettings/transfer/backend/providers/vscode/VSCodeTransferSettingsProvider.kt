// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vscode

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.TransferSettingsConfiguration
import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.providers.TransferSettingsProvider
import com.intellij.ide.startup.importSettings.transfer.backend.models.IdeVersion
import com.intellij.ide.startup.importSettings.ui.representation.TransferSettingsRightPanelChooser
import com.intellij.util.SmartList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths

open class VSCodeTransferSettingsProvider(scope: CoroutineScope) : TransferSettingsProvider {

  override val transferableIdeId: TransferableIdeId = TransferableIdeId.VSCode

  protected open val processor: VSCodeSettingsProcessor = VSCodeSettingsProcessor(scope)

  override val name: String = "Visual Studio Code"

  protected open val id: String = "VSCode"

  override fun isAvailable(): Boolean = true

  override suspend fun hasDataToImport(): Boolean =
    withContext(Dispatchers.IO) {
      isVSCodeDetected()
    }

  override fun getIdeVersions(skipIds: List<String>): SmartList<IdeVersion> = when (isVSCodeDetected()) {
    true -> SmartList(getIdeVersion())
    false -> SmartList()
  }

  private val cachedIdeVersion by lazy {
    IdeVersion(
      transferableIdeId,
      null,
      id,
      AllIcons.TransferSettings.Vscode,
      name,
      settingsInit = { processor.getProcessedSettings() },
      provider = this
    )
  }

  private fun getIdeVersion(): IdeVersion {
    return cachedIdeVersion
  }

  private fun isVSCodeDetected() =
    Files.isDirectory(Paths.get(processor.vsCodeHome))
    && processor.isInstanceRecentEnough()
    && processor.willDetectAtLeastSomething()

  override fun getRightPanel(ideV: IdeVersion, config: TransferSettingsConfiguration): TransferSettingsRightPanelChooser
    = VSCodeTransferSettingsRightPanelChooser(ideV, config)
}

private class VSCodeTransferSettingsRightPanelChooser(
  ide: IdeVersion,
  config: TransferSettingsConfiguration
) : TransferSettingsRightPanelChooser(ide, config) {

  override fun getBottomComponentFactory() = null
}
