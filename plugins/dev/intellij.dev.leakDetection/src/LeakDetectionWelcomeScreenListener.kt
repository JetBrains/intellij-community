// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.ide.AppLifecycleListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit

// Let the welcome screen settle and short-lived objects (the just-closed project's frame, etc.) clear before scanning.
private const val WELCOME_SCREEN_DELAY_SECONDS = 10L

/**
 * Auto-runs leak detection when the Welcome Screen appears, gated by the (default-off)
 * `dev.leak.detection.runOnWelcomeScreen` registry key and internal mode.
 **/
internal class LeakDetectionWelcomeScreenListener : AppLifecycleListener {
  override fun projectFrameClosed(): Unit = scheduleDetection()

  private fun scheduleDetection() {
    val app = ApplicationManager.getApplication()
    if (!app.isInternal) return
    if (!Registry.`is`("dev.leak.detection.runOnWelcomeScreen")) return

    AppExecutorUtil.getAppScheduledExecutorService().schedule(
      {
        app.invokeLater {
          if (WelcomeFrame.getInstance() != null) {
            NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
              .createNotification(DevLeakDetectionBundle.message("notification.content.starting.soon"), NotificationType.INFORMATION)
              .notify(null)
          }
        }
      },
      1, TimeUnit.SECONDS,
    )

    AppExecutorUtil.getAppScheduledExecutorService().schedule(
      {
        app.invokeLater {
          // Run only when the Welcome Screen is actually visible, e.g. not when closing one of several open projects.
          if (WelcomeFrame.getInstance() != null) {
            runLeakDetectionInBackground(project = null)
          }
        }
      },
      WELCOME_SCREEN_DELAY_SECONDS, TimeUnit.SECONDS,
    )
  }
}
