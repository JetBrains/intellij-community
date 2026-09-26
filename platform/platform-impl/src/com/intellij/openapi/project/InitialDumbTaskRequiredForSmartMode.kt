// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.startup.StartupActivity.RequiredForSmartMode
import com.intellij.serviceContainer.AlreadyDisposedException

internal class InitialDumbTaskRequiredForSmartMode(private val project: Project) : DumbModeTask() {
  override fun performInDumbMode(indicator: ProgressIndicator) {
    val logger = logger<InitialDumbTaskRequiredForSmartMode>()
    for (activity in ExtensionPointName<RequiredForSmartMode>("com.intellij.requiredForSmartModeStartupActivity").extensionList) {
      ProgressManager.checkCanceled()

      logger.assertTrue(indicator == ProgressManager.getGlobalProgressIndicator(),
                        "There might be visual inconsistencies: " +
                        "launched activities can only use thread's global indicator.")
      indicator.pushState()
      try {
        logger.info("Running task required for smart mode: $activity")
        activity.runActivity(project)
      }
      catch (@Suppress("IncorrectCancellationExceptionHandling") _: AlreadyDisposedException) {
        // we cannot yet make performInDumbMode as suspend, so we cannot make `runActivity` as suspend,
        // so we cannot handle project disposable correctly
        return
      }
      finally {
        indicator.popState()
        logger.info("Finished task required for smart mode: $activity")
      }
    }
  }
}