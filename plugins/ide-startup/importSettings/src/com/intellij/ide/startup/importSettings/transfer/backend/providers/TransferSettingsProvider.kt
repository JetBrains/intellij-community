// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers

import com.intellij.ide.startup.importSettings.TransferSettingsConfiguration
import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.models.BaseIdeVersion
import com.intellij.ide.startup.importSettings.transfer.backend.models.IdeVersion
import com.intellij.ide.startup.importSettings.ui.representation.TransferSettingsRightPanelChooser

interface TransferSettingsProvider { // ex. AbstractTransferSettingsProvider
  val name: String

  val transferableIdeId: TransferableIdeId
  fun isAvailable(): Boolean
  suspend fun hasDataToImport(): Boolean
  fun getIdeVersions(skipIds: List<String>): List<BaseIdeVersion>
  fun getRightPanel(ideV: IdeVersion, config: TransferSettingsConfiguration): TransferSettingsRightPanelChooser? = null
}
