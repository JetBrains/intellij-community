// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.updateSettings.impl.ExternalUpdateManager

/**
 * @author Konstantin Bulenkov
 */
class UpdateManagerUsagesCollector : ApplicationUsagesCollector() {
  override fun getMetrics(): Set<MetricEvent> = setOf(
    newMetric("Update Manager", when (val updateManager = ExternalUpdateManager.ACTUAL) {
      ExternalUpdateManager.UNKNOWN -> "Other"
      null -> "IDE"
      else -> updateManager.toolName
    }))

  override fun getGroupId(): String = "platform.installer"

  override fun getVersion(): Int = 1
}
