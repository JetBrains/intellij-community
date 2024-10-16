// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.startup.StartupActivity.RequiredForSmartMode

internal class InitialDumbTaskRequiredForSmartMode(private val project: Project) : DumbModeTask() {
  override fun performInDumbMode(indicator: ProgressIndicator) {
    val activities = ExtensionPointName<RequiredForSmartMode>("com.intellij.requiredForSmartModeStartupActivity").extensionList
    val logger = logger<InitialDumbTaskRequiredForSmartMode>()
    for (activity in activities) {
      ProgressManager.checkCanceled()
      logger<InitialDumbTaskRequiredForSmartMode>().assertTrue(indicator == ProgressManager.getGlobalProgressIndicator(),
                                                               "There might be visual inconsistencies: " +
                                                               "launched activities can only use thread's global indicator.")
      indicator.pushState()
      try {
        logger.info("Running task required for smart mode: $activity")
        activity.runActivity(project)
      }
      finally {
        indicator.popState()
        logger.info("Finished task required for smart mode: $activity")
      }
    }
  }
}