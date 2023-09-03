// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.ide.CommandLineInspectionProgressReporter
import com.intellij.ide.warmup.WarmupLogger
import com.intellij.ide.warmup.WarmupStatus
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.GradleWarmupConfigurator
import org.jetbrains.plugins.gradle.service.notification.ExternalAnnotationsProgressNotificationManager

class GradleHeadlessLoggingProjectActivity(val scope: CoroutineScope) : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (WarmupStatus.currentStatus(ApplicationManager.getApplication()) != WarmupStatus.InProgress || !Registry.`is`(
        "ide.warmup.use.predicates")) {
      return
    }
    val progressManager = ExternalSystemProgressNotificationManager.getInstance()
    addTaskNotificationListener(progressManager)
    addStateNotificationListener(project, progressManager)
    addAnnotationListener()
  }

  private fun addTaskNotificationListener(progressManager: ExternalSystemProgressNotificationManager) {
    val listener = GradleWarmupConfigurator.LoggingNotificationListener(object : CommandLineInspectionProgressReporter {
      override fun reportError(message: String?) {
        if (message == null) {
          return
        }
        WarmupLogger.error(message, null)
      }

      override fun reportMessage(minVerboseLevel: Int, message: String?) {
        if (message == null) {
          return
        }
        WarmupLogger.message(message)
      }
    })
    progressManager.addNotificationListener(listener)
    scope.launch {
      awaitCancellationAndInvoke {
        progressManager.removeNotificationListener(listener)
      }
    }
  }

  private fun addStateNotificationListener(project: Project, progressManager: ExternalSystemProgressNotificationManager) {
    val notificationListener = GradleWarmupConfigurator.StateNotificationListener(project)
    progressManager.addNotificationListener(notificationListener)
    scope.launch {
      awaitCancellationAndInvoke {
        progressManager.removeNotificationListener(notificationListener)
      }
    }
  }

  private fun addAnnotationListener() {
    val externalAnnotationsNotificationManager = ExternalAnnotationsProgressNotificationManager.getInstance()
    val externalAnnotationsProgressListener = GradleWarmupConfigurator.StateExternalAnnotationNotificationListener()

    externalAnnotationsNotificationManager.addNotificationListener(externalAnnotationsProgressListener)
    scope.launch {
      awaitCancellationAndInvoke {
        externalAnnotationsNotificationManager.removeNotificationListener(externalAnnotationsProgressListener)
      }
    }
  }
}