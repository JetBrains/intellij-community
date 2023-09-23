// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.updater

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.internal.statistic.service.fus.collectors.ProjectFUStateUsagesLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.minutes

internal class StatisticsStateCollectorsScheduler : ApplicationInitializedListener {
  companion object {
    private val LOG_APPLICATION_STATE_SMART_MODE_DELAY = 1.minutes

    // avoid overlapping logging from periodic scheduler and OneTimeLogger (long indexing case)
    internal val allowExecution = AtomicBoolean(true) // TODO get rid of this
  }

  override suspend fun execute(asyncScope: CoroutineScope) : Unit = blockingContext {
    ApplicationManager.getApplication().service<FUStateUsagesLogger>() // init service
  }

  internal class MyStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      // smart mode is not available when LightEdit is active
      if (LightEdit.owns(project)) {
        return
      }

      FUCounterUsageLogger.getInstance() // init service
      project.service<ProjectFUStateUsagesLogger>() // init service

      if (allowExecution.get()) {
        DumbService.getInstance(project).runWhenSmart {
          // wait until all projects will exit dumb mode
          if (ProjectManager.getInstance().openProjects.any { p -> !p.isDisposed && p.isInitialized && DumbService.getInstance(p).isDumb }) {
            return@runWhenSmart
          }
          scheduleLogging(project)
        }
      }
    }

    // check and execute only once because several projects can exit dumb mode at the same time
    private fun scheduleLogging(project: Project) {
      if (allowExecution.getAndSet(false)) {
        project.coroutineScope.launch {
          delay(LOG_APPLICATION_STATE_SMART_MODE_DELAY)
          FUStateUsagesLogger.getInstance().scheduleLogApplicationStatesOnStartup()
        }
      }
    }
  }
}