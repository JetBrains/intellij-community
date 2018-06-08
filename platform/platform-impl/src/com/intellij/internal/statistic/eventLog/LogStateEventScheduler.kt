// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.concurrency.JobScheduler
import com.intellij.internal.statistic.eventLog.FeatureUsageStateEvents.logConfigurationState
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.impl.stores.StoreUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.util.pico.DefaultPicoContainer
import java.util.concurrent.TimeUnit

class LogStateEventScheduler : StartupActivity {
  private val LOG = Logger.getInstance("com.intellij.internal.statistic.eventLog.LogStateEventScheduler")

  private val INITIAL_DELAY = 24 * 60
  private val PERIOD_DELAY = 24 * 60

  override fun runActivity(project: Project) {
    if (!FeatureUsageLogger.isEnabled()) {
      return
    }

    JobScheduler.getScheduler().scheduleWithFixedDelay(
      { logConfigStateEvents() },
      INITIAL_DELAY.toLong(), PERIOD_DELAY.toLong(), TimeUnit.MINUTES
    )
  }

  private fun logConfigStateEvents() {
    recordInitializedComponents(ApplicationManager.getApplication())
    for (project in ProjectManager.getInstance().openProjects) {
      if (!project.isDefault) {
        recordInitializedComponents(project)
      }
    }
  }

  private fun recordInitializedComponents(componentManager: ComponentManager) {
    ApplicationManager.getApplication().invokeLater {
      val picoContainer = componentManager.picoContainer
      if (picoContainer is DefaultPicoContainer) {
        val components = picoContainer.getInstantiatedComponents(PersistentStateComponent::class.java)
        for (component in components) {
          try {
            val spec = StoreUtil.getStateSpec(component)
            logConfigurationState(spec.name, component, componentManager is Application)
          }
          catch (e: Exception) {
            LOG.warn("Error during configuration recording", e)
          }
        }
      }
    }
  }
}

