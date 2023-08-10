// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType

object BreakpointsUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("debugger.breakpoints.usage", 4)
  private val WITHIN_SESSION_FIELD = EventFields.Boolean("within_session")
  val TYPE_FIELD = EventFields.StringValidatedByCustomRule("type", BreakpointsUtilValidator::class.java)
  private val BREAKPOINT_ADDED = GROUP.registerVarargEvent("breakpoint.added", WITHIN_SESSION_FIELD,
                                                           EventFields.PluginInfo, TYPE_FIELD)
  private val BREAKPOINT_VERIFIED = GROUP.registerEvent("breakpoint.verified", EventFields.Long("time"))

  @JvmStatic
  fun reportNewBreakpoint(breakpoint: XBreakpoint<*>, type: XBreakpointType<*, *>, withinSession: Boolean) {
    if (breakpoint is XBreakpointBase<*, *, *>) {
      val data = mutableListOf<EventPair<*>>()
      data.addAll(getType(type))
      data.add(WITHIN_SESSION_FIELD.with(withinSession))
      BREAKPOINT_ADDED.log(breakpoint.project, data)
    }
  }

  @JvmStatic
  fun reportBreakpointVerified(breakpoint: XBreakpoint<*>, time: Long) {
    if (breakpoint is XBreakpointBase<*, *, *>) {
      BREAKPOINT_VERIFIED.log(breakpoint.project, time)
    }
  }

  override fun getGroup(): EventLogGroup = GROUP
}