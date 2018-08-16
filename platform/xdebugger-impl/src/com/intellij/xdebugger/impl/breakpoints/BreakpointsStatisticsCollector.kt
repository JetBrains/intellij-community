// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.ensureProperKey
import com.intellij.internal.statistic.utils.getCountingUsage
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl

/**
 * @author egor
 */

class BreakpointsStatisticsCollector : ProjectUsagesCollector() {
  override fun getGroupId(): String = "statistics.debugger.breakpoints"

  override fun getUsages(project: Project): MutableSet<UsageDescriptor> {
    return ReadAction.compute<MutableSet<UsageDescriptor>, Exception> {
      val breakpointManager = XDebuggerManagerImpl.getInstance(project).breakpointManager as XBreakpointManagerImpl

      val res = XBreakpointUtil.breakpointTypes()
        .filter { it.isSuspendThreadSupported() }
        .filter { breakpointManager.getBreakpointDefaults(it).getSuspendPolicy() != it.getDefaultSuspendPolicy() }
        .map {
          UsageDescriptor(
            ensureProperKey("not.default.suspend.${breakpointManager.getBreakpointDefaults(it).getSuspendPolicy()}.${it.getId()}"))
        }
        .toMutableSet()

      if (breakpointManager.allGroups.isNotEmpty()) {
        res.add(UsageDescriptor("using.groups"))
      }

      val breakpoints = breakpointManager.allBreakpoints.filter { !breakpointManager.isDefaultBreakpoint(it) }

      res.add(getCountingUsage("total", breakpoints.size))

      val disabled = breakpoints.count { !it.isEnabled() }
      if (disabled > 0) {
        res.add(getCountingUsage("total.disabled", disabled))
      }

      val nonSuspending = breakpoints.count { it.getSuspendPolicy() == SuspendPolicy.NONE }
      if (nonSuspending > 0) {
        res.add(getCountingUsage("total.non.suspending", nonSuspending))
      }

      if (breakpoints.any { !XDebuggerUtilImpl.isEmptyExpression(it.getConditionExpression()) }) {
        res.add(UsageDescriptor("using.condition"))
      }

      if (breakpoints.any { !XDebuggerUtilImpl.isEmptyExpression(it.getLogExpressionObject()) }) {
        res.add(UsageDescriptor("using.log.expression"))
      }

      if (breakpoints.any { it is XLineBreakpoint<*> && it.isTemporary }) {
        res.add(UsageDescriptor("using.temporary"))
      }

      if (breakpoints.any { breakpointManager.dependentBreakpointManager.isMasterOrSlave(it) }) {
        res.add(UsageDescriptor("using.dependent"))
      }

      if (breakpoints.any { it.isLogMessage() }) {
        res.add(UsageDescriptor("using.log.message"))
      }

      if (breakpoints.any { it.isLogStack() }) {
        res.add(UsageDescriptor("using.log.stack"))
      }

      res
    }
  }
}