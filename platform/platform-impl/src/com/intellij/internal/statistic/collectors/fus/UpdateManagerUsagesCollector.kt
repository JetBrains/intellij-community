// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.updateSettings.impl.ExternalUpdateManager

/**
 * @author Konstantin Bulenkov
 */
class UpdateManagerUsagesCollector : ApplicationUsagesCollector() {
  override fun getMetrics(): MutableSet<MetricEvent> {
    val updateManager = when (ExternalUpdateManager.ACTUAL) {
      ExternalUpdateManager.TOOLBOX -> "Toolbox App"
      ExternalUpdateManager.SNAP -> "Snap"
      else -> "IDE"
    }
    return mutableSetOf(newMetric("Update Manager", updateManager))
  }

  override fun getGroupId(): String {
    return "platform.installer"
  }

  override fun getVersion(): Int {
    return 1
  }
}