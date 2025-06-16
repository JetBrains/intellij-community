// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping

import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule
import com.intellij.xdebugger.breakpoints.ui.XBreakpointsGroupingPriorities

private class XBreakpointGroupingByTypeRule<B : Any>
  : XBreakpointGroupingRule<B, XBreakpointTypeGroup>("XBreakpointGroupingByTypeRule", XDebuggerBundle.message("breakpoints.group.by.type.label")) {
  override fun isAlwaysEnabled(): Boolean {
    return true
  }

  override fun getPriority(): Int {
    return XBreakpointsGroupingPriorities.BY_TYPE
  }

  override fun getGroup(b: B): XBreakpointTypeGroup? {
    val proxy = b.asBreakpointProxyOrNull()
    if (proxy != null) {
      return XBreakpointTypeGroup(proxy.type)
    }
    return null
  }
}