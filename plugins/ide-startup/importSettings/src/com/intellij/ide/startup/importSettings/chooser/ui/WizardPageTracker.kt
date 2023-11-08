// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ide.bootstrap.IdeStartupWizardCollector
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.util.concurrency.ThreadingAssertions
import java.time.Duration

private val logger = logger<WizardPageTracker>()

class WizardPageTracker(private val stage: StartupWizardStage?) {

  private var lastEnterTimeNs: Long? = null
  fun onEnter() {
    if (stage == null) return
    ThreadingAssertions.assertEventDispatchThread()
    if (lastEnterTimeNs != null) {
      logger.error("Double enter on page $stage without leave.")
    }

    lastEnterTimeNs = System.nanoTime()
  }

  fun onLeave() {
    if (stage == null) return
    ThreadingAssertions.assertEventDispatchThread()

    val enterTimeNs = lastEnterTimeNs
    val leaveTimeNs = System.nanoTime()
    if (enterTimeNs == null) {
      return
    }

    val duration = Duration.ofNanos(leaveTimeNs - enterTimeNs)
    IdeStartupWizardCollector.logStartupStageTime(stage, duration)
    lastEnterTimeNs = null
  }
}