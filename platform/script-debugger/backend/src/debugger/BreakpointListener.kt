// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger

import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

@ApiStatus.NonExtendable
interface BreakpointListener : EventListener {
  @ApiStatus.Internal
  fun resolved(breakpoint: Breakpoint)

  @ApiStatus.Internal
  fun errorOccurred(breakpoint: Breakpoint, errorMessage: String?)

  @ApiStatus.Internal
  fun nonProvisionalBreakpointRemoved(breakpoint: Breakpoint) {
  }
}