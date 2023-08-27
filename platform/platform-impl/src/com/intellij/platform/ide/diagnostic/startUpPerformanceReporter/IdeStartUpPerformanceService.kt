// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.diagnostic.startUpPerformanceReporter

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.platform.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter
import kotlinx.coroutines.CoroutineScope

private class IdeStartUpPerformanceService(coroutineScope: CoroutineScope) : StartUpPerformanceReporter(coroutineScope) {
  @Volatile
  private var editorRestoringTillHighlighted = false

  @Volatile
  private var projectOpenedActivitiesPassed = false

  override fun projectDumbAwareActivitiesFinished() {
    projectOpenedActivitiesPassed = true
    if (editorRestoringTillHighlighted) {
      completed()
    }
  }

  override fun editorRestoringTillHighlighted() {
    editorRestoringTillHighlighted = true
    if (projectOpenedActivitiesPassed) {
      completed()
    }
  }

  private fun completed() {
    StartUpMeasurer.stopPluginCostMeasurement()
    // don't report statistic from here if we want to measure project import duration
    if (!java.lang.Boolean.getBoolean("idea.collect.project.import.performance")) {
      keepAndLogStats(ProjectManagerEx.getOpenProjects().firstOrNull()?.name ?: "unknown")
    }
  }
}