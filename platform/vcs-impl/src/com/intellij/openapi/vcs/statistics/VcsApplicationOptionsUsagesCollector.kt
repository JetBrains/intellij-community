// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.vcs.VcsApplicationSettings

internal class VcsApplicationOptionsUsagesCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("vcs.application.configuration", 4)
  private val NON_MODAL_COMMIT = GROUP.registerVarargEvent("non.modal.commit", EventFields.Enabled)

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun getMetrics(): Set<MetricEvent> {
    val defaultSettings = VcsApplicationSettings()
    val appSettings = VcsApplicationSettings.getInstance()

    return mutableSetOf<MetricEvent>().apply {
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.COMMIT_FROM_LOCAL_CHANGES }, NON_MODAL_COMMIT)
    }
  }
}