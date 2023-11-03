// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings

import com.intellij.ide.startup.importSettings.chooser.productChooser.ProductChooserDialog
import com.intellij.ide.startup.importSettings.chooser.ui.MultiplePageDialog
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ide.bootstrap.IdeStartupWizard
import com.intellij.platform.ide.bootstrap.isIdeStartupWizardEnabled
import com.jetbrains.rd.util.reactive.fire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal class IdeStartupWizardImpl : IdeStartupWizard {

  override suspend fun run() {
    if (!isIdeStartupWizardEnabled) return

    logger.info("Initial startup wizard is enabled. Will start the wizard.")
    coroutineScope {
      // Fire-and-forget call to warm up the external settings transfer
      val settingsService = SettingsService.getInstance()
      async { settingsService.getExternalService().warmUp() }

      withContext(Dispatchers.EDT) {
        MultiplePageDialog.show(
          ProductChooserDialog(),
          { settingsService.importCancelled.fire() },
          title = ApplicationNamesInfo.getInstance().fullProductName
        )
      }
    }
  }
}

private val logger = logger<IdeStartupWizardImpl>()
