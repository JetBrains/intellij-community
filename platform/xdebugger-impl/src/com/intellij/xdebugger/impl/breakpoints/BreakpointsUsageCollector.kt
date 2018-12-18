// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.internal.statistic.service.fus.collectors.FUSProjectUsageTrigger
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsageTriggerCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator
import com.intellij.internal.statistic.utils.getPluginType
import com.intellij.xdebugger.breakpoints.XBreakpoint

/**
 * @author egor
 */
class BreakpointsUsageCollector : ProjectUsageTriggerCollector() {
  override fun getGroupId(): String = "statistics.debugger.breakpoints.usage"

  companion object {
    @JvmStatic
    fun reportUsage(breakpoint: XBreakpoint<*>, featureId: String) {
      if (breakpoint is XBreakpointBase<*, *, *> && getPluginType(breakpoint.type.javaClass).isSafeToReport()) {
        FUSProjectUsageTrigger.getInstance(breakpoint.getProject()).trigger(BreakpointsUsageCollector::class.java,
                                                                            UsageDescriptorKeyValidator.ensureProperKey(featureId))
      }
    }
  }
}