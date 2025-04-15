// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping

import com.intellij.icons.AllIcons
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule
import com.intellij.xdebugger.breakpoints.ui.XBreakpointsGroupingPriorities
import javax.swing.Icon

internal class XBreakpointFileGroupingRule<B : Any>
  : XBreakpointGroupingRule<B, XBreakpointFileGroup>("by-file", XDebuggerBundle.message("rule.name.group.by.file")) {
  override fun getPriority(): Int {
    return XBreakpointsGroupingPriorities.BY_FILE
  }

  override fun getGroup(breakpoint: B): XBreakpointFileGroup? {
    val proxy = breakpoint.asBreakpointProxyOrNull() ?: return null
    if (!proxy.type.isLineBreakpoint) {
      return null
    }
    val position = proxy.getSourcePosition()

    if (position == null) return null

    val file = position.getFile()

    return XBreakpointFileGroup(file)
  }

  override fun getIcon(): Icon? {
    return AllIcons.Actions.GroupByFile
  }
}
