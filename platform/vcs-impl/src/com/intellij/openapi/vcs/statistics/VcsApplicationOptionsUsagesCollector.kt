// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.vcs.VcsApplicationSettings

class VcsApplicationOptionsUsagesCollector : ApplicationUsagesCollector() {
  override fun getGroupId(): String = "vcs.application.configuration"

  override fun getMetrics(): Set<MetricEvent> {
    val settings = VcsApplicationSettings.getInstance()
    val defaultSettings = VcsApplicationSettings()

    return mutableSetOf<MetricEvent>().apply {
      addBoolIfDiffers(this, settings, defaultSettings, { it.COMMIT_FROM_LOCAL_CHANGES }, "commit.from.local.changes")
    }
  }
}