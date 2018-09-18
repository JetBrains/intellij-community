// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator
import com.intellij.openapi.project.Project

/**
 * @author egor
 */
class BreakpointsUsageCollector : ProjectUsageTriggerCollector() {
  override fun getGroupId(): String = "statistics.debugger.breakpoints.usage"

  companion object {
    @JvmStatic
    fun reportUsage(project: Project, featureId: String) {
      FUSProjectUsageTrigger.getInstance(project).trigger(BreakpointsUsageCollector::class.java,
                                                          UsageDescriptorKeyValidator.ensureProperKey(featureId))
    }
  }
}