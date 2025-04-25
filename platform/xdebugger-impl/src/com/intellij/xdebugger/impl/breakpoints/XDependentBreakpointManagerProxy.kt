// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XDependentBreakpointManagerProxy {
  fun getMasterBreakpoint(breakpoint: XBreakpointProxy): XBreakpointProxy?
  fun isLeaveEnabled(breakpoint: XBreakpointProxy): Boolean
  fun clearMasterBreakpoint(breakpoint: XBreakpointProxy)
  fun setMasterBreakpoint(breakpoint: XBreakpointProxy, masterBreakpoint: XBreakpointProxy, selected: Boolean)

  class Monolith(val dependentManager: XDependentBreakpointManager): XDependentBreakpointManagerProxy {
    override fun getMasterBreakpoint(breakpoint: XBreakpointProxy): XBreakpointProxy? {
      val monolith = breakpoint as? XBreakpointProxy.Monolith ?: return null
      val master = dependentManager.getMasterBreakpoint(monolith.breakpoint) ?: return null
      return (master as XBreakpointBase<*, *, *>).asProxy()
    }

    override fun isLeaveEnabled(breakpoint: XBreakpointProxy): Boolean {
      val monolith = breakpoint as? XBreakpointProxy.Monolith ?: return false
      return dependentManager.isLeaveEnabled(monolith.breakpoint)
    }

    override fun clearMasterBreakpoint(breakpoint: XBreakpointProxy) {
      val monolith = breakpoint as? XBreakpointProxy.Monolith ?: return
      dependentManager.clearMasterBreakpoint(monolith.breakpoint)
    }

    override fun setMasterBreakpoint(breakpoint: XBreakpointProxy, masterBreakpoint: XBreakpointProxy, selected: Boolean) {
      val breakpointMonolith = breakpoint as? XBreakpointProxy.Monolith ?: return
      val masterMonolith = masterBreakpoint as? XBreakpointProxy.Monolith ?: return
      dependentManager.setMasterBreakpoint(breakpointMonolith.breakpoint, masterMonolith.breakpoint, selected)
    }
  }
}