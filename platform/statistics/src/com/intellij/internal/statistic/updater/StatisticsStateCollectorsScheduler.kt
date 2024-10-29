// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.updater

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.internal.statistic.service.fus.collectors.ProjectFUStateUsagesLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

private val LOG_APPLICATION_STATE_SMART_MODE_DELAY = 1.minutes

// avoid overlapping logging from periodic scheduler and OneTimeLogger (long indexing case)
internal val allowExecution = AtomicBoolean(true) // TODO get rid of this

private class StatisticsStateCollectorsScheduler : ApplicationActivity {
  override suspend fun execute() {
    // init service
    serviceAsync<FUStateUsagesLogger>()
  }

  class MyStartupActivity : ProjectActivity {
    init {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override suspend fun execute(project: Project) {
      // smart mode is not available when LightEdit is active
      if (LightEdit.owns(project)) {
        return
      }

      // init service
      serviceAsync<FUCounterUsageLogger>()
      // init service
      project.serviceAsync<ProjectFUStateUsagesLogger>()

      if (!allowExecution.get()) {
        return
      }

      project.waitForSmartMode()

      // wait until all projects exit dumb mode
      if (serviceAsync<ProjectManager>().openProjects.any { p -> !p.isDisposed && p.isInitialized && DumbService.getInstance(p).isDumb }) {
        return
      }

      // check and execute only once because several projects can exit dumb mode at the same time
      if (allowExecution.getAndSet(false)) {
        delay(LOG_APPLICATION_STATE_SMART_MODE_DELAY)
        serviceAsync<FUStateUsagesLogger>().logApplicationStatesOnStartup()
      }
    }
  }
}