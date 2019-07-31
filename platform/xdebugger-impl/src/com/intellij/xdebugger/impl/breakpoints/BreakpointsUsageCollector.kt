// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.FUCounterUsageLogger
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType

/**
 * @author egor
 */
class BreakpointsUsageCollector {
  companion object {
    const val GROUP = "debugger.breakpoints.usage"

    @JvmStatic
    fun reportNewBreakpoint(breakpoint: XBreakpoint<*>, type: XBreakpointType<*, *>, withinSession: Boolean) {
      if (breakpoint is XBreakpointBase<*, *, *>) {
        val data = FeatureUsageData()
        addType(type, data)
        data.addData("within_session", withinSession)
        FUCounterUsageLogger.getInstance().logEvent(breakpoint.getProject(), GROUP, "breakpoint.added", data)
      }
    }

    @JvmStatic
    fun reportBreakpointVerified(breakpoint: XBreakpoint<*>, time: Long) {
      if (breakpoint is XBreakpointBase<*, *, *>) {
        FUCounterUsageLogger.getInstance().logEvent(breakpoint.getProject(), GROUP, "breakpoint.verified",
                                                    FeatureUsageData().addData("time", time))
      }
    }
  }
}