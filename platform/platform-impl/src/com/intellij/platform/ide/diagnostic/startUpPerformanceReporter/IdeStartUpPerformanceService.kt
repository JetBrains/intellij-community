// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.platform.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter
import com.intellij.platform.ide.progress.ModalTaskOwner
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.atomic.AtomicBoolean

internal class IdeStartUpPerformanceService(coroutineScope: CoroutineScope) : StartUpPerformanceReporter(coroutineScope) {
  @Volatile
  private var editorRestoringTillHighlighted = false

  @Volatile
  private var projectOpenedActivitiesPassed = false

  private val isReported = AtomicBoolean()

  init {
    if (perfFilePath != null) {
      val projectName = ProjectManagerEx.getOpenProjects().firstOrNull()?.name ?: "unknown"
      ApplicationManager.getApplication().messageBus.simpleConnect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
        override fun appWillBeClosed(isRestart: Boolean) {
          if (!isReported.compareAndSet(false, true)) {
            return
          }

          runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
            logStats(projectName)
          }
        }
      })
    }
  }

  override fun projectDumbAwareActivitiesFinished() {
    if (ApplicationManagerEx.isInIntegrationTest()) {
      thisLogger().info("startup phase completed: project dumb aware activities finished")
    }
    projectOpenedActivitiesPassed = true
    if (editorRestoringTillHighlighted) {
      completed()
    }
  }

  override fun editorRestoringTillHighlighted() {
    if (ApplicationManagerEx.isInIntegrationTest()) {
      thisLogger().info("startup phase completed: editor highlighted")
    }
    editorRestoringTillHighlighted = true
    if (projectOpenedActivitiesPassed) {
      completed()
    }
  }

  private fun completed() {
    if (!isReported.compareAndSet(false, true)) {
      return
    }

    StartUpMeasurer.stopPluginCostMeasurement()
    // don't report statistic from here if we want to measure project import duration
    if (!java.lang.Boolean.getBoolean("idea.collect.project.import.performance")) {
      keepAndLogStats(ProjectManagerEx.getOpenProjects().firstOrNull()?.name ?: "unknown")
    }
  }
}