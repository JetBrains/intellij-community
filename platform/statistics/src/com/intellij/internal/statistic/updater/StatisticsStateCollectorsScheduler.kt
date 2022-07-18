// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.updater

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.internal.statistic.service.fus.collectors.FUStateUsagesLogger
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectPostStartupActivity
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

  override fun componentsInitialized() {
    if (!StatisticsUploadAssistant.isSendAllowed()) {
      return
    }

    allowExecution.set(true)

    // avoid overlapping logging from periodic scheduler and OneTimeLogger (long indexing case)
    ApplicationManager.getApplication().coroutineScope.launch {
      delay(LOG_APPLICATION_STATES_INITIAL_DELAY)
      allowExecution.set(false)
      FUStateUsagesLogger.create().logApplicationStates()
      while (true) {
        delay(LOG_APPLICATION_STATES_DELAY)
        FUStateUsagesLogger.create().logApplicationStates()
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
          FUStateUsagesLogger.create().logProjectStates(project, EmptyProgressIndicator())

          while (true) {
            delay(LOG_PROJECTS_STATES_DELAY)
            FUStateUsagesLogger.create().logProjectStates(project, EmptyProgressIndicator())
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
          FUStateUsagesLogger.create().logApplicationStatesOnStartup()
        }
      }
    }
  }
}