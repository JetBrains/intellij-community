// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings

import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingController
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ide.bootstrap.IdeStartupWizard
import com.intellij.platform.ide.bootstrap.isIdeStartupWizardEnabled
import com.intellij.util.concurrency.ThreadingAssertions
import com.jetbrains.rd.util.reactive.fire
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

internal class IdeStartupWizardImpl : IdeStartupWizard {

  override suspend fun run() {
    if (!isIdeStartupWizardEnabled) return

    logger.info("Initial startup wizard is enabled. Will start the wizard.")
    ThreadingAssertions.assertEventDispatchThread()

    coroutineScope {
      // Fire-and-forget call to warm up the external settings transfer
      val settingsService = SettingsService.getInstance()
      async { settingsService.getExternalService().warmUp() }

      OnboardingController.getInstance().startImport(
        { settingsService.importCancelled.fire() },
        title = ApplicationNamesInfo.getInstance().fullProductName
      )
    }
  }
}

private val logger = logger<IdeStartupWizardImpl>()
