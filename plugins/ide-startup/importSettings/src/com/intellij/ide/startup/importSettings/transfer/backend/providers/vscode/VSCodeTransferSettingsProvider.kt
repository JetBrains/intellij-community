// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vscode

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.TransferSettingsConfiguration
import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.models.IdeVersion
import com.intellij.ide.startup.importSettings.providers.TransferSettingsProvider
import com.intellij.ide.startup.importSettings.providers.vscode.VSCodeSettingsProcessor.Companion.vsCodeHome
import com.intellij.ide.startup.importSettings.ui.representation.TransferSettingsRightPanelChooser
import com.intellij.util.SmartList
import java.nio.file.Files
import java.nio.file.Paths

class VSCodeTransferSettingsProvider : TransferSettingsProvider {

  override val transferableIdeId = TransferableIdeId.VSCode

  private val processor = VSCodeSettingsProcessor()
  override val name: String
    get() = "Visual Studio Code"

  override fun isAvailable(): Boolean = true

  override fun getIdeVersions(skipIds: List<String>): SmartList<IdeVersion> = when (isVSCodeDetected()) {
    true -> SmartList(getIdeVersion())
    false -> SmartList()
  }

  private val cachedIdeVersion by lazy {
    IdeVersion(
      TransferableIdeId.VSCode,
      null,
      "VSCode",
      AllIcons.TransferSettings.Vscode,
      "Visual Studio Code",
      settingsInit = { processor.getProcessedSettings() },
      provider = this
    )
  }

  private fun getIdeVersion(): IdeVersion {
    return cachedIdeVersion
  }

  private fun isVSCodeDetected() =
    Files.isDirectory(Paths.get(vsCodeHome))
    && processor.isInstanceRecentEnough()
    && processor.willDetectAtLeastSomething()

  override fun getRightPanel(ideV: IdeVersion, config: TransferSettingsConfiguration): TransferSettingsRightPanelChooser
    = VSCodeTransferSettingsRightPanelChooser(ideV, config)
}

private class VSCodeTransferSettingsRightPanelChooser(private val ide: IdeVersion, config: TransferSettingsConfiguration) : TransferSettingsRightPanelChooser(ide, config) {
  override fun getBottomComponentFactory() = null
}
