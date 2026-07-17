// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry

/**
 * Auto-runs leak detection when the Welcome Screen appears, gated by the (default-off)
 * `dev.leak.detection.runOnWelcomeScreen` registry key and internal mode.
 **/
internal class LeakDetectionWelcomeScreenListener : AppLifecycleListener {
  override fun projectFrameClosed() {
    val app = ApplicationManager.getApplication()
    if (!app.isInternal) return
    if (!Registry.`is`("dev.leak.detection.runOnWelcomeScreen")) return

    LeakDetectionRunner.getInstance().scheduleDetectionOnWelcomeScreen()
  }
}
