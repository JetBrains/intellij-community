// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings

import com.intellij.ide.isIdeStartupWizardEnabled
import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingController
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.ide.startup.importSettings.data.StartupWizardService
import com.intellij.ide.startup.importSettings.statistics.ImportSettingsEventsCollector
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ide.bootstrap.IdeStartupWizard
import com.intellij.util.concurrency.ThreadingAssertions
import com.jetbrains.rd.util.reactive.fire
import kotlinx.coroutines.coroutineScope

private class IdeStartupWizardImpl : IdeStartupWizard {
  override suspend fun run() {
    if (!isIdeStartupWizardEnabled) return

    logger.info("Initial startup wizard is enabled. Will start the wizard.")
    ThreadingAssertions.assertEventDispatchThread()

    coroutineScope {
      // Fire-and-forget call to warm up the external settings transfer
      val settingsService = SettingsService.getInstance()
      settingsService.warmUp()

      if (settingsService.shouldShowImport()) {
        logger.info("Settings service reports that we should show the import wizard.")
        OnboardingController.getInstance().startImport(
          { settingsService.importCancelled.fire() },
          titleGetter = { ApplicationNamesInfo.getInstance().fullProductName }
        )
        return@coroutineScope
      }

      logger.info("No import options available: skipping the import wizard.")
      ImportSettingsEventsCollector.importDialogNotShown()

      val wizardService = StartupWizardService.getInstance()
      if (wizardService != null) {
        logger.info("A startup wizard service is activated.")
        OnboardingController.getInstance().startWizard(
          cancelCallback = { settingsService.importCancelled.fire() },
          titleGetter = { ApplicationNamesInfo.getInstance().fullProductName }
        )
      }

      logger.info("No active startup wizard service detected either: skipping the startup wizard.")
    }
  }
}

private val logger = logger<IdeStartupWizardImpl>()
