// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui.grouping

import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy
import com.intellij.xdebugger.impl.breakpoints.asProxy

internal fun Any.asBreakpointProxyOrNull(): XBreakpointProxy? {
  val breakpoint = this
  if (breakpoint is XBreakpointProxy) {
    return breakpoint
  }
  if (breakpoint is XBreakpointBase<*, *, *>) {
    return breakpoint.asProxy()
  }
  return null
}