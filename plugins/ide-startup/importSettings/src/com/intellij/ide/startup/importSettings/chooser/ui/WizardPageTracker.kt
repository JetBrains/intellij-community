// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ide.bootstrap.IdeStartupWizardCollector
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.util.concurrency.ThreadingAssertions
import java.time.Duration

private val logger = logger<WizardPageTracker>()

internal class WizardPageTracker {
  private var lastEnterTimeNs: Long? = null
  private var currentStage: StartupWizardStage? = null
  fun onEnter(stage: StartupWizardStage?) {
    ThreadingAssertions.assertEventDispatchThread()
    if (currentStage != null) {
      logger.error("Logic error: entered page $stage while still on page $currentStage.")
    }

    lastEnterTimeNs = System.nanoTime()
    currentStage = stage
  }

  fun onLeave() {
    val stage = currentStage ?: return
    ThreadingAssertions.assertEventDispatchThread()

    val enterTimeNs = lastEnterTimeNs
    val leaveTimeNs = System.nanoTime()
    if (enterTimeNs == null) {
      return
    }

    val duration = Duration.ofNanos(leaveTimeNs - enterTimeNs)
    IdeStartupWizardCollector.logStartupStageTime(stage, duration)

    lastEnterTimeNs = null
    currentStage = null
  }
}