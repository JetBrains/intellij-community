// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer

import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.data.BaseSetting
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.util.containers.forEachLoggingErrors

interface ThirdPartyProductSettingItemProvider {
  companion object {
    private val EP_NAME = ExtensionPointName<ThirdPartyProductSettingItemProvider>("com.intellij.transferSettings.thirdPartyProductSettingItem")

    fun generateSettingItems(productId: TransferableIdeId, thirdPartyProductSettings: Settings): List<BaseSetting> {
      val result = mutableListOf<BaseSetting>()
      EP_NAME.extensionList.forEachLoggingErrors(logger) {
        result.addAll(it.generateSettingItems(productId, thirdPartyProductSettings))
      }
      return result
    }
  }

  /**
   * Generates items for the third-party settings import dialog.
   */
  fun generateSettingItems(productId: TransferableIdeId, thirdPartyProductSettings: Settings): List<BaseSetting>
}

private val logger = logger<ThirdPartyProductSettingItemProvider>()
