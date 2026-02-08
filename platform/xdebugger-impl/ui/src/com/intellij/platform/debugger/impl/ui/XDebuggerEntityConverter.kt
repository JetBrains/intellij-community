// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.ui

import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.rpc.XValueId
import com.intellij.platform.debugger.impl.shared.XDebuggerMonolithAccessPoint
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.frame.XValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
object XDebuggerEntityConverter {
  /**
   * For a given [XDebugSessionProxy] finds the corresponding [XDebugSession] instance.
   *
   * Always returns `null` on the frontend.
   *
   * Use this method to implement monolith-only features with a split debugger enabled.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun getSession(proxy: XDebugSessionProxy): XDebugSession? {
    return XDebuggerMonolithAccessPoint.find { it.getSession(proxy) }
  }

  /**
   * For a given [XDebugSessionProxy] finds the corresponding [XDebugSession] instance only if split debugger is disabled.
   *
   * Use this method to keep the exact same behavior in monolith and remdev when a split debugger is enabled.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun getSessionNonSplitOnly(proxy: XDebugSessionProxy): XDebugSession? {
    return XDebuggerMonolithAccessPoint.find { it.getSessionNonSplitOnly(proxy) }
  }

  @ApiStatus.Internal
  @JvmStatic
  fun asProxy(session: XDebugSession): XDebugSessionProxy {
    return XDebuggerMonolithAccessPoint.find { it.asProxy(session) }
           ?: error("No XDebuggerMonolithAccessPoint implementation that can convert $session found. " +
                    "XDebuggerMonolithAccessPointImpl should be registered in a shared module and always be able to convert sessions.")
  }

  /**
   * For a given [XValueId] finds the corresponding [XValue] instance.
   *
   * Always returns `null` on the frontend.
   *
   * Use this method to implement monolith-only features with a split debugger enabled.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun getValue(valueId: XValueId): XValue? {
    return XDebuggerMonolithAccessPoint.find { it.getValue(valueId) }
  }

  /**
   * For a given breakpoint type ID finds the corresponding [XBreakpointType] instance.
   *
   * Always returns `null` on the frontend.
   *
   * Use this method to implement monolith-only features with a split debugger enabled.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun getBreakpointType(typeId: String): XBreakpointType<*, *>? {
    return XDebuggerMonolithAccessPoint.find { it.getBreakpointType(typeId) }
  }

  /**
   * For a given [XBreakpointId] finds the corresponding [XBreakpoint] instance.
   *
   * Always returns `null` on the frontend.
   *
   * Use this method to implement monolith-only features with a split debugger enabled.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun getBreakpoint(breakpointId: XBreakpointId): XBreakpoint<*>? {
    return XDebuggerMonolithAccessPoint.find { it.getBreakpoint(breakpointId) }
  }

  /**
   * For a given [XBreakpoint] returns the corresponding [XBreakpointId].
   *
   * Returns `null` if the breakpoint is not a backend implementation instance.
   *
   * Use this method to convert breakpoints to IDs for UI operations.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun getBreakpointId(breakpoint: XBreakpoint<*>): XBreakpointId? {
    return XDebuggerMonolithAccessPoint.find { it.getBreakpointId(breakpoint) }
  }

  /**
   * For a given [XBreakpoint] returns the corresponding [XBreakpointProxy].
   *
   * Returns `null` if the breakpoint is not a backend implementation instance.
   *
   * Use this method to convert breakpoints to proxies for UI operations.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun asProxy(breakpoint: XBreakpoint<*>): XBreakpointProxy? {
    return XDebuggerMonolithAccessPoint.find { it.asProxy(breakpoint) }
  }
}