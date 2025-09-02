// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping

import com.intellij.icons.AllIcons
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule
import javax.swing.Icon

/**
 * @author Egor
 */
private class XBreakpointCustomGroupingRule<B : Any>
  : XBreakpointGroupingRule<B, XBreakpointCustomGroup>("by-group", XDebuggerBundle.message("breakpoints.show.user.groups")) {
  override fun getPriority(): Int {
    return 1200
  }

  override fun isAlwaysEnabled(): Boolean {
    return true
  }

  override fun getGroup(breakpoint: B): XBreakpointCustomGroup? {
    val proxy = breakpoint.asBreakpointProxyOrNull() ?: return null
    val name = proxy.getGroup()?.takeIf { it.isNotEmpty() }
    if (name == null) {
      return null
    }
    return XBreakpointCustomGroup(name, proxy.project)
  }

  override fun getIcon(): Icon? {
    return AllIcons.Nodes.Folder
  }
}
