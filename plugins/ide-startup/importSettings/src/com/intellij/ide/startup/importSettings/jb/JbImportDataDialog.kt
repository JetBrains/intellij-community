// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.jb

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.ide.startup.importSettings.transfer.TransferSettingsProgressIndicator

class JbImportDataDialog(private val productInfo: JbProductInfo) : ImportFromProduct {
  override val from: DialogImportItem = DialogImportItem(productInfo, NameMappings.getIcon(productInfo.codeName, IconProductSize.LARGE)!!)
  override val to: DialogImportItem = DialogImportItem.self()
  override val message = ImportSettingsBundle.message("transfer.settings.message", productInfo.name)
  override val progress = TransferSettingsProgressIndicator()
}