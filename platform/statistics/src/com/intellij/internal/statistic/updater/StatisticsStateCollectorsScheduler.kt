// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.updater

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectPostStartupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

internal class StatisticsStateCollectorsScheduler : ApplicationInitializedListener {
  companion object {
    private val LOG_APPLICATION_STATES_INITIAL_DELAY = 10.minutes
    private val LOG_APPLICATION_STATES_DELAY = 24.hours
    private val LOG_APPLICATION_STATE_SMART_MODE_DELAY = 1.minutes
    private val LOG_PROJECTS_STATES_INITIAL_DELAY = 5.minutes
    private val LOG_PROJECTS_STATES_DELAY = 12.hours
    private const val REDUCE_DELAY_FLAG_KEY = "fus.internal.reduce.initial.delay"

    private val allowExecution = AtomicBoolean(true)
  }

  override suspend fun execute(asyncScope: CoroutineScope) {
    asyncScope.launch {
      if (!StatisticsUploadAssistant.isSendAllowed()) {
        return@launch
      }

      // avoid overlapping logging from periodic scheduler and OneTimeLogger (long indexing case)
      allowExecution.set(true)

      delay(LOG_APPLICATION_STATES_INITIAL_DELAY)
      allowExecution.set(false)
      FUStateUsagesLogger.getInstance().logApplicationStates()
      while (true) {
        delay(LOG_APPLICATION_STATES_DELAY)
        FUStateUsagesLogger.getInstance().logApplicationStates()
      }
    }
  }

  internal class MyStartupActivity : ProjectPostStartupActivity {
    override suspend fun execute(project: Project) {
      // smart mode is not available when LightEdit is active
      if (LightEdit.owns(project)) {
        return
      }

      // wait until initial indexation will be finished
      DumbService.getInstance(project).runWhenSmart {
        project.coroutineScope.launch {
          val reduceInitialDelay = System.getProperty(REDUCE_DELAY_FLAG_KEY).toBoolean()
          if (!reduceInitialDelay) {
            delay(LOG_PROJECTS_STATES_INITIAL_DELAY)
          }
          FUStateUsagesLogger.getInstance().logProjectStates(project)

          while (true) {
            delay(LOG_PROJECTS_STATES_DELAY)
            FUStateUsagesLogger.getInstance().logProjectStates(project)
          }
        }
      }
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
          FUStateUsagesLogger.getInstance().logApplicationStatesOnStartup()
        }
      }
    }
  }
}