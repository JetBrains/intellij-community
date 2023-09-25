// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

private const val INTELLIJ_STARTUP_WIZARD_CLASS_PROPERTY = "intellij.startup-wizard.class"

private val LOG: Logger
  get() = logger<IdeStartupWizard>()

internal val isStartupWizardEnabled: Boolean =
  !System.getProperty(INTELLIJ_STARTUP_WIZARD_CLASS_PROPERTY).isNullOrBlank()

internal suspend fun runStartupWizard() {
  if (!isStartupWizardEnabled) return
  if (!ConfigImportHelper.isNewUser()) return

  LOG.info("Entering startup wizard workflow.")

  waitForAppManagerInitialState()

  val className = System.getProperty(INTELLIJ_STARTUP_WIZARD_CLASS_PROPERTY)
  LOG.info("Passing execution control to $className.")
  val wizardClass = Class.forName(className)
  val instance = wizardClass.getDeclaredConstructor().newInstance() as IdeStartupWizard
  instance.run()
}

private suspend fun waitForAppManagerInitialState() {
  val latch = ApplicationManagerEx.getInitialStartState()
  if (latch == null) error("Cannot get initial startup state")
  LOG.info("Waiting for app manager initial state.")
  withContext(Dispatchers.IO) {
    runInterruptible {
      latch.await()
    }
  }
}

interface IdeStartupWizard {
  suspend fun run()
}
