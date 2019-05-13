// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.internal.statistic.eventLog.FeatureUsageGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator
import com.intellij.internal.statistic.utils.createData
import com.intellij.xdebugger.breakpoints.XBreakpoint

private val GROUP = FeatureUsageGroup("statistics.debugger.breakpoints.usage",1)

/**
 * @author egor
 */
class BreakpointsUsageCollector {
  companion object {
    @JvmStatic
    fun reportUsage(breakpoint: XBreakpoint<*>, featureId: String) {
      if (breakpoint is XBreakpointBase<*, *, *>) {
        FeatureUsageLogger.log(GROUP, UsageDescriptorKeyValidator.ensureProperKey(featureId),
                               createData(breakpoint.getProject(), null))
      }
    }
  }
}