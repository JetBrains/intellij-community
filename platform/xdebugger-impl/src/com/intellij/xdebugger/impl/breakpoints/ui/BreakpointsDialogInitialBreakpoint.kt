// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui

import com.intellij.xdebugger.impl.rpc.XBreakpointId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface BreakpointsDialogInitialBreakpoint {
  data class BreakpointId(val id: XBreakpointId) : BreakpointsDialogInitialBreakpoint

  data class GenericBreakpoint(val breakpoint: Any) : BreakpointsDialogInitialBreakpoint
}