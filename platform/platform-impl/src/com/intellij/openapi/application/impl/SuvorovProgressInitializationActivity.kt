// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.progress.util.SuvorovProgress
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus
import javax.swing.SwingUtilities

@ApiStatus.Internal
class SuvorovProgressInitializationActivity : AppLifecycleListener {
  override fun appStarted() {
    if (!Registry.`is`("ide.event.dispatch.during.lock.freezes")) {
      return
    }
    SwingUtilities.invokeLater {
      getGlobalThreadingSupport().setLockAcquisitionInterceptor(SuvorovProgress::dispatchEventsUntilComputationCompletes)
    }
  }
}