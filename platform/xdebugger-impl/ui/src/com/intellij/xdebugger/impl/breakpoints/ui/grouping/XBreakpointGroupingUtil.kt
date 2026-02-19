// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping

import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter
import com.intellij.xdebugger.breakpoints.XBreakpoint

internal fun Any.asBreakpointProxyOrNull(): XBreakpointProxy? {
  val breakpoint = this
  if (breakpoint is XBreakpointProxy) {
    return breakpoint
  }
  if (breakpoint is XBreakpoint<*>) {
    return XDebuggerEntityConverter.asProxy(breakpoint)
  }
  return null
}