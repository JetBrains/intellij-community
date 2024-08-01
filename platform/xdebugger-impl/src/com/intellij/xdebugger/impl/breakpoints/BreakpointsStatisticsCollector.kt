// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.validator.ValidationResultType
import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.breakpoints.BreakpointsUsageCollector.TYPE_FIELD

private class BreakpointsStatisticsCollector : ProjectUsagesCollector() {
  private val GROUP = EventLogGroup("debugger.breakpoints", 4)
  private val SUSPEND_POLICY_FIELD = EventFields.Enum("suspendPolicy", SuspendPolicy::class.java)
  private val NOT_DEFAULT_SUSPEND = GROUP.registerVarargEvent("not.default.suspend", EventFields.Enabled,
                                                              SUSPEND_POLICY_FIELD, TYPE_FIELD, EventFields.PluginInfo)
  private val TOTAL = GROUP.registerEvent("total", EventFields.Count)
  private val TOTAL_DISABLED = GROUP.registerEvent("total.disabled", EventFields.Count)
  private val TOTAL_NON_SUSPENDING = GROUP.registerEvent("total.non.suspending", EventFields.Count)
  private val USING_GROUPS = GROUP.registerEvent("using.groups", EventFields.Enabled)
  private val USING_CONDITION = GROUP.registerEvent("using.condition", EventFields.Enabled)
  private val USING_LOG_EXPRESSION = GROUP.registerEvent("using.log.expression", EventFields.Enabled)
  private val USING_TEMPORARY = GROUP.registerEvent("using.temporary", EventFields.Enabled)
  private val USING_DEPENDENT = GROUP.registerEvent("using.dependent", EventFields.Enabled)
  private val USING_LOG_MESSAGE = GROUP.registerEvent("using.log.message", EventFields.Enabled)
  private val USING_LOG_STACK = GROUP.registerEvent("using.log.stack", EventFields.Enabled)

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun requiresReadAccess(): Boolean = true

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val breakpointManager = XDebuggerManagerImpl.getInstance(project).breakpointManager as XBreakpointManagerImpl

    val res = XBreakpointType.EXTENSION_POINT_NAME.extensionList
      .asSequence()
      .filter { it.isSuspendThreadSupported }
      .filter { breakpointManager.getBreakpointDefaults(it).suspendPolicy != it.defaultSuspendPolicy }
      .map {
        ProgressManager.checkCanceled()
        val data = mutableListOf<EventPair<*>>()
        data.add(SUSPEND_POLICY_FIELD.with(breakpointManager.getBreakpointDefaults(it).suspendPolicy))
        data.addAll(getType(it))
        data.add(EventFields.Enabled.with(true))
        NOT_DEFAULT_SUSPEND.metric(data)
      }
      .toMutableSet()

    if (breakpointManager.allGroups.isNotEmpty()) {
      res.add(USING_GROUPS.metric(true))
    }

    val breakpoints = breakpointManager.allBreakpoints.filter { !breakpointManager.isDefaultBreakpoint(it) }

    res.add(TOTAL.metric(breakpoints.size))

    res.add(TOTAL_DISABLED.metric(breakpoints.count { !it.isEnabled }))
    res.add(TOTAL_NON_SUSPENDING.metric(breakpoints.count { it.suspendPolicy == SuspendPolicy.NONE }))

    if (breakpoints.any { !XDebuggerUtilImpl.isEmptyExpression(it.conditionExpression) }) {
      res.add(USING_CONDITION.metric(true))
    }

    if (breakpoints.any { !XDebuggerUtilImpl.isEmptyExpression(it.logExpressionObject) }) {
      res.add(USING_LOG_EXPRESSION.metric(true))
    }

    if (breakpoints.any { it is XLineBreakpoint<*> && it.isTemporary }) {
      res.add(USING_TEMPORARY.metric(true))
    }

    if (breakpoints.any { breakpointManager.dependentBreakpointManager.isMasterOrSlave(it) }) {
      res.add(USING_DEPENDENT.metric(true))
    }

    if (breakpoints.any { it.isLogMessage }) {
      res.add(USING_LOG_MESSAGE.metric(true))
    }

    if (breakpoints.any { it.isLogStack }) {
      res.add(USING_LOG_STACK.metric(true))
    }
    return res
  }
}

internal fun getType(type: XBreakpointType<*, *>) : List<EventPair<*>> {
  val data = mutableListOf<EventPair<*>>()
  val info = getPluginInfo(type.javaClass)
  data.add(EventFields.PluginInfo.with(info))
  data.add(TYPE_FIELD.with(if (info.isDevelopedByJetBrains()) type.id else "custom"))
  return data
}

internal class BreakpointsUtilValidator : CustomValidationRule() {
  override fun getRuleId(): String {
    return "breakpoint"
  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if ("custom" == data) return ValidationResultType.ACCEPTED

    for (breakpoint in XBreakpointType.EXTENSION_POINT_NAME.extensionList) {
      if (StringUtil.equals(breakpoint.id, data)) {
        val info = getPluginInfo(breakpoint.javaClass)
        return if (info.isDevelopedByJetBrains()) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
      }
    }
    return ValidationResultType.REJECTED
  }
}