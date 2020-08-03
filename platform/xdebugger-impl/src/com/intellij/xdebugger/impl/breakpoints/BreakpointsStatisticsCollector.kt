// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newBooleanMetric
import com.intellij.internal.statistic.beans.newCounterMetric
import com.intellij.internal.statistic.eventLog.FeatureUsageData
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

class BreakpointsStatisticsCollector : ProjectUsagesCollector() {
  override fun getGroupId(): String = "debugger.breakpoints"

  override fun getVersion(): Int = 2

  override fun requiresReadAccess(): Boolean = true

  override fun getMetrics(project: Project): MutableSet<MetricEvent> {
    val breakpointManager = XDebuggerManagerImpl.getInstance(project).breakpointManager as XBreakpointManagerImpl

    val res = XBreakpointType.EXTENSION_POINT_NAME.extensionList
      .asSequence()
      .filter { it.isSuspendThreadSupported() }
      .filter { breakpointManager.getBreakpointDefaults(it).getSuspendPolicy() != it.getDefaultSuspendPolicy() }
      .map {
        ProgressManager.checkCanceled()
        val data = FeatureUsageData()
        data.addData("suspendPolicy", breakpointManager.getBreakpointDefaults(it).getSuspendPolicy().toString())
        addType(it, data)
        newBooleanMetric("not.default.suspend", true, data)
      }
      .toMutableSet()

    if (breakpointManager.allGroups.isNotEmpty()) {
      res.add(newBooleanMetric("using.groups", true))
    }

    val breakpoints = breakpointManager.allBreakpoints.filter { !breakpointManager.isDefaultBreakpoint(it) }

    res.add(newCounterMetric("total", breakpoints.size))

    res.add(newCounterMetric("total.disabled", breakpoints.count { !it.isEnabled() }))
    res.add(newCounterMetric("total.non.suspending", breakpoints.count { it.getSuspendPolicy() == SuspendPolicy.NONE }))

    if (breakpoints.any { !XDebuggerUtilImpl.isEmptyExpression(it.getConditionExpression()) }) {
      res.add(newBooleanMetric("using.condition", true))
    }

    if (breakpoints.any { !XDebuggerUtilImpl.isEmptyExpression(it.getLogExpressionObject()) }) {
      res.add(newBooleanMetric("using.log.expression", true))
    }

    if (breakpoints.any { it is XLineBreakpoint<*> && it.isTemporary }) {
      res.add(newBooleanMetric("using.temporary", true))
    }

    if (breakpoints.any { breakpointManager.dependentBreakpointManager.isMasterOrSlave(it) }) {
      res.add(newBooleanMetric("using.dependent", true))
    }

    if (breakpoints.any { it.isLogMessage() }) {
      res.add(newBooleanMetric("using.log.message", true))
    }

    if (breakpoints.any { it.isLogStack() }) {
      res.add(newBooleanMetric("using.log.stack", true))
    }
    return res
  }
}

fun addType(type: XBreakpointType<*, *>, data: FeatureUsageData) {
  val info = getPluginInfo(type.javaClass)
  data.addPluginInfo(info)
  data.addData("type", if (info.isDevelopedByJetBrains()) type.getId() else "custom")
}

class BreakpointsUtilValidator : CustomValidationRule() {
  override fun acceptRuleId(ruleId: String?): Boolean {
    return "breakpoint" == ruleId
  }

  override fun doValidate(data: String, context: EventContext): ValidationResultType {
    if ("custom".equals(data)) return ValidationResultType.ACCEPTED

    for (breakpoint in XBreakpointType.EXTENSION_POINT_NAME.extensions) {
      if (StringUtil.equals(breakpoint.getId(), data)) {
        val info = getPluginInfo(breakpoint.javaClass)
        return if (info.isDevelopedByJetBrains()) ValidationResultType.ACCEPTED else ValidationResultType.REJECTED
      }
    }
    return ValidationResultType.REJECTED
  }
}