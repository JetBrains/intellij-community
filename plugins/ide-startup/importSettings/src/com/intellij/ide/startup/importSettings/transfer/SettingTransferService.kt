// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.startup.importSettings.data.BaseSetting
import com.intellij.ide.startup.importSettings.data.DataForSave
import com.intellij.ide.startup.importSettings.data.DialogImportData
import com.intellij.ide.startup.importSettings.data.ExternalService
import com.intellij.ide.startup.importSettings.data.IconProductSize
import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import javax.swing.Icon

@Service
class SettingTransferService : ExternalService {

  companion object {

    fun getInstance(): SettingTransferService = service()

    private val logger = logger<SettingTransferService>()
  }

  override fun products(): List<Product> {
    logger.error("TODO")
    return listOf()
  }

  override fun getSettings(itemId: String): List<BaseSetting> {
    logger.error("TODO")
    return listOf()
  }

  override fun getProductIcon(itemId: String,
                              size: IconProductSize): Icon? {
    logger.error("TODO")
    return null
  }

  override fun importSettings(productId: String,
                              data: List<DataForSave>): DialogImportData {
    TODO("Not yet implemented")
  }
}
