// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints

import org.jetbrains.annotations.ApiStatus

/**
 * Initial line breakpoint state that must be applied before the breakpoint is registered and
 * [XBreakpointListener.breakpointAdded] observers can see it.
 *
 * @param verticalPlacement vertical placement of the breakpoint
 * @param suspendPolicy certain suspend policy declared when the breakpoint is created,
 *  or null if no such policy is declared
 * @param logExpressionIfEnabled log expression of the breakpoint, or null if the breakpoint shouldn't be logging.
 *  Empty string is a valid log expression and must be preserved.
 * @param isTemporary whether the breakpoint should be removed once hit
 */
@ApiStatus.Experimental
class XLineBreakpointAdditionalInfo private constructor(
  val verticalPlacement: XLineBreakpointVerticalPlacement,
  val suspendPolicy: SuspendPolicy?,
  val logExpressionIfEnabled: String?,
  val isTemporary: Boolean,
) {

  @ApiStatus.Experimental
  class Builder {
    private var verticalPlacement: XLineBreakpointVerticalPlacement = XLineBreakpointVerticalPlacement.ON_LINE
    private var suspendPolicy: SuspendPolicy? = null
    private var logExpressionIfEnabled: String? = null
    private var isTemporary: Boolean = false

    fun build(): XLineBreakpointAdditionalInfo {
      return XLineBreakpointAdditionalInfo(verticalPlacement, suspendPolicy, logExpressionIfEnabled, isTemporary)
    }

    fun setVerticalPlacement(verticalPlacement: XLineBreakpointVerticalPlacement): Builder {
      this@Builder.verticalPlacement = verticalPlacement
      return this
    }

    fun setSuspendPolicy(suspendPolicy: SuspendPolicy?): Builder {
      this@Builder.suspendPolicy = suspendPolicy
      return this
    }

    fun setLogExpressionIfEnabled(logExpressionIfEnabled: String?): Builder {
      this@Builder.logExpressionIfEnabled = logExpressionIfEnabled
      return this
    }

    fun setTemporary(isTemporary: Boolean): Builder {
      this@Builder.isTemporary = isTemporary
      return this
    }
  }
}
