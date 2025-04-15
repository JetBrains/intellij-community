// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.breakpoints.XDependentBreakpointManagerProxy

internal class FrontendXDependentBreakpointManagerProxy : XDependentBreakpointManagerProxy {
  override fun getMasterBreakpoint(breakpoint: XBreakpointProxy): XBreakpointProxy? {
    // TODO: implement
    return null
  }

  override fun isLeaveEnabled(breakpoint: XBreakpointProxy): Boolean {
    // TODO: implement
    return false
  }

  override fun clearMasterBreakpoint(breakpoint: XBreakpointProxy) {
    // TODO: implement
  }

  override fun setMasterBreakpoint(breakpoint: XBreakpointProxy, masterBreakpoint: XBreakpointProxy, selected: Boolean) {
    // TODO: implement
  }
}