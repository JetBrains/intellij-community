// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.execution.ui.UIExperiment
import com.intellij.internal.statistic.eventLog.EventLogConfiguration.Companion.getInstance
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.PlatformUtils

class DebuggerUIExperimentCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  companion object {
    private const val NUMBER_OF_EXPERIMENT_GROUPS = 3
    private val GROUP = EventLogGroup("debugger.ui.experiment", 1)
    private val START = GROUP.registerEvent("start", EventFields.Int("group"))
    private val STOP = GROUP.registerEvent("stop")

    @JvmStatic
    fun startExperiment(): Boolean {
      var res = false
      if (isEnabled()) {
        val experimentGroup = getExperimentGroup()
        START.log(experimentGroup)
        res = experimentGroup == NUMBER_OF_EXPERIMENT_GROUPS - 1
      }
      UIExperiment.setNewDebuggerUIEnabled(res)
      return res
    }

    @JvmStatic
    fun stopExperiment() {
      if (isEnabled()) {
        STOP.log()
      }
    }

    private fun getExperimentGroup(): Int {
      val registryExperimentGroup = Registry.intValue("debugger.ui.experiment.group")
      return if (registryExperimentGroup >= 0) registryExperimentGroup
      else getInstance().bucket %
           NUMBER_OF_EXPERIMENT_GROUPS
    }

    private fun isEnabled(): Boolean = ApplicationManager.getApplication().isEAP &&
                                       PlatformUtils.isIntelliJ() &&
                                       Registry.`is`("debugger.ui.experiment.enabled") &&
                                       StatisticsUploadAssistant.isSendAllowed() &&
                                       !ApplicationManager.getApplication().isInternal
  }
}