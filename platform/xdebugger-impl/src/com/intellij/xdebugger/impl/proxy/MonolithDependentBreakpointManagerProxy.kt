// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.proxy

import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDependentBreakpointManagerProxy
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointManager

internal class MonolithDependentBreakpointManagerProxy(val dependentManager: XDependentBreakpointManager) : XDependentBreakpointManagerProxy {
  override fun getMasterBreakpoint(breakpoint: XBreakpointProxy): XBreakpointProxy? {
    val monolith = breakpoint as? MonolithBreakpointProxy ?: return null
    val master = dependentManager.getMasterBreakpoint(monolith.breakpoint) ?: return null
    return (master as XBreakpointBase<*, *, *>).asProxy()
  }

  override fun isLeaveEnabled(breakpoint: XBreakpointProxy): Boolean {
    val monolith = breakpoint as? MonolithBreakpointProxy ?: return false
    return dependentManager.isLeaveEnabled(monolith.breakpoint)
  }

  override fun clearMasterBreakpoint(breakpoint: XBreakpointProxy) {
    val monolith = breakpoint as? MonolithBreakpointProxy ?: return
    dependentManager.clearMasterBreakpoint(monolith.breakpoint)
  }

  override fun setMasterBreakpoint(breakpoint: XBreakpointProxy, masterBreakpoint: XBreakpointProxy, selected: Boolean) {
    val breakpointMonolith = breakpoint as? MonolithBreakpointProxy ?: return
    val masterMonolith = masterBreakpoint as? MonolithBreakpointProxy ?: return
    dependentManager.setMasterBreakpoint(breakpointMonolith.breakpoint, masterMonolith.breakpoint, selected)
  }
}