// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

// Show the "starting soon" hint shortly after the Welcome Screen appears.
private val WELCOME_SCREEN_NOTIFICATION_DELAY = 1.seconds

// Let the Welcome Screen settle and short-lived objects (the just-closed project's frame, etc.) clear before scanning.
private val WELCOME_SCREEN_DETECTION_DELAY = 10.seconds

@Service
internal class LeakDetectionRunner(private val coroutineScope: CoroutineScope) {

  private val welcomeScreenRequests = Channel<Unit>(CONFLATED)

  init {
    coroutineScope.launch {
      welcomeScreenRequests.consumeAsFlow().collectLatest {
        runDetectionOnWelcomeScreen()
      }
    }
  }

  fun scheduleDetectionOnWelcomeScreen() {
    welcomeScreenRequests.trySend(Unit)
  }

  private suspend fun runDetectionOnWelcomeScreen() {
    delay(WELCOME_SCREEN_NOTIFICATION_DELAY)
    withContext(Dispatchers.UI) {
      if (WelcomeFrame.getInstance() != null) {
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
          .createNotification(DevLeakDetectionBundle.message("notification.content.starting.soon"), NotificationType.INFORMATION)
          .notify(null)
      }
    }

    delay(WELCOME_SCREEN_DETECTION_DELAY - WELCOME_SCREEN_NOTIFICATION_DELAY)
    withContext(Dispatchers.UI) {
      // Run only when the Welcome Screen is actually visible, e.g. not when closing one of several open projects.
      if (WelcomeFrame.getInstance() != null) {
        runLeakDetectionInBackground(project = null)
      }
    }
  }

  /** Runs [ProjectLeakDetector] on a background thread under a progress indicator, then reports the results. */
  fun runLeakDetectionInBackground(project: Project?) {
    coroutineScope.launch {
      val leaks = if (project != null) {
        withBackgroundProgress(project, DevLeakDetectionBundle.message("progress.title.detecting.leaks"), cancellable = true) {
          ProjectLeakDetector().detect()
        }
      }
      else {
        withModalProgress(ModalTaskOwner.guess(),
                          DevLeakDetectionBundle.message("modal.progress.title.detecting.leaks"),
                          TaskCancellation.cancellable()) {
          ProjectLeakDetector().detect()
        }
      }

      withContext(Dispatchers.UI) {
        reporter().report(project, leaks)
      }
    }
  }

  fun reporter(): LeakReporter = LeakReporter(coroutineScope)

  companion object {
    fun getInstance(): LeakDetectionRunner = service()
  }
}
