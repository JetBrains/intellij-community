// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException

private const val INTELLIJ_STARTUP_WIZARD_CLASS_PROPERTY = "intellij.startup-wizard.class"

private val LOG: Logger
  get() = logger<IdeStartupWizard>()

internal val isStartupWizardEnabled: Boolean =
  System.getProperty(INTELLIJ_STARTUP_WIZARD_CLASS_PROPERTY).isNotBlank()

internal suspend fun runStartupWizard() {
  if (!isStartupWizardEnabled) return
  if (!ConfigImportHelper.isNewUser()) return

  LOG.runAndLogException {
    val className = System.getProperty(INTELLIJ_STARTUP_WIZARD_CLASS_PROPERTY)
    val wizardClass = Class.forName(className) ?: run {
      LOG.error("Could not find class $className")
      return
    }
    val instance = wizardClass.getDeclaredConstructor().newInstance() as IdeStartupWizard
    instance.run()
  }
}

interface IdeStartupWizard {
  suspend fun run()
}
