// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.util

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.rpc.XValueId
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.models.findValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object XDebugMonolithUtils {
  private fun isMonolithOrBackend(): Boolean = FrontendApplicationInfo.getFrontendType() is FrontendType.Monolith

  /**
   * Provides access to the backend debug session by its ID.
   * This method will return null in RemDev mode.
   * Use this method only for components that should be available in monolith only.
   */
  @JvmStatic
  fun findSessionById(sessionId: XDebugSessionId): XDebugSessionImpl? {
    if (!isMonolithOrBackend()) return null
    return sessionId.findValue()
  }

  /**
   * Provides access to the backend [XValue] by its ID.
   * This method will return null in RemDev mode.
   * Use this method only for components that should be available in monolith only.
   */
  fun findXValueById(xValueId: XValueId): XValue? {
    if (!isMonolithOrBackend()) return null
    return BackendXValueModel.findById(xValueId)?.xValue
  }

  /**
   * Provides access to the backend [XBreakpointType] by its ID.
   * This method will return null in RemDev mode.
   * Use this method only for components that should be available in monolith only.
   */
  fun findBreakpointTypeById(id: String): XBreakpointType<*, *>? {
    if (!isMonolithOrBackend()) return null
    return XBreakpointUtil.findType(id)
  }

  /**
   * Provides access to the backend [XBreakpointBase] by its ID.
   * This method will return null in RemDev mode.
   * Use this method only for components that should be available in monolith only.
   */
  @JvmStatic
  fun findBreakpointById(breakpointId: XBreakpointId): XBreakpointBase<*, *, *>? {
    if (!isMonolithOrBackend()) return null
    return breakpointId.findValue()
  }
}