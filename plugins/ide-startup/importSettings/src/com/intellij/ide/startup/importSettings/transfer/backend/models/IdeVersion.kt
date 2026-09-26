// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.models

import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.TransferableIdeVersionId
import com.intellij.ide.startup.importSettings.fus.TransferSettingsCollector
import com.intellij.ide.startup.importSettings.models.BaseIdeVersion
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.providers.TransferSettingsProvider
import java.util.Date
import javax.swing.Icon

class IdeVersion(
  val transferableId: TransferableIdeId,
  val transferableVersion: TransferableIdeVersionId?,
  id: String,
  icon: Icon,
  name: String,
  subName: String? = null,
  settingsInit: () -> Settings,
  val lastUsed: Date? = null,
  val provider: TransferSettingsProvider,
  val sortKey: Int = 0
) : BaseIdeVersion(id, icon, name, subName) {
  val settingsCache by lazy {
    settingsInit().also {
      TransferSettingsCollector.logIdeSettingsDiscovered(this, it)
    }
  }
}
