// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState

/**
 * The default [SmartModeScheduler] for a process that does not load `intellij.platform.lang.impl.backend`.
 * Such a process has no scanning and no indexing, so this scheduler reports smart mode at all times.
 */
internal class AlwaysSmartModeScheduler(private val project: Project) : SmartModeScheduler {
  override fun canRunSmart(): Boolean = true

  override fun getCurrentMode(): Int = 0

  override fun runWhenSmart(runnable: Runnable) {
    val application = ApplicationManager.getApplication()
    if (application.isDispatchThread) {
      runnable.run()
    }
    else {
      application.invokeLater(runnable, ModalityState.defaultModalityState(), project.disposed)
    }
  }
}
