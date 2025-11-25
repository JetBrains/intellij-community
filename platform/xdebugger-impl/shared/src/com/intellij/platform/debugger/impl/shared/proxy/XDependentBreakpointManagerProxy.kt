// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared.proxy

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XDependentBreakpointManagerProxy {
  fun getMasterBreakpoint(breakpoint: XBreakpointProxy): XBreakpointProxy?
  fun isLeaveEnabled(breakpoint: XBreakpointProxy): Boolean
  fun clearMasterBreakpoint(breakpoint: XBreakpointProxy)
  fun setMasterBreakpoint(breakpoint: XBreakpointProxy, masterBreakpoint: XBreakpointProxy, selected: Boolean)
}