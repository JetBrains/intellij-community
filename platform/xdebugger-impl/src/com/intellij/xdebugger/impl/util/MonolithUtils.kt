// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.util

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.models.findValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MonolithUtils {
  /**
   * Provides access to the backend debug session by its ID.
   * This method will return null in RemDev mode.
   * Use this method only for components that should be available in monolith only.
   */
  @JvmStatic
  fun findSessionById(sessionId: XDebugSessionId): XDebugSessionImpl? {
    if (FrontendApplicationInfo.getFrontendType() !is FrontendType.Monolith) return null
    return sessionId.findValue()
  }

  /**
   * Provides access to the backend [XValue] by its ID.
   * This method will return null in RemDev mode.
   * Use this method only for components that should be available in monolith only.
   */
  fun findXValueById(xValueId: XValueId): XValue? {
    return BackendXValueModel.findById(xValueId)?.xValue
  }

  /**
   * Provides access to the backend [XBreakpointType] by its ID.
   * This method will return null in RemDev mode.
   * Use this method only for components that should be available in monolith only.
   */
  fun findBreakpointTypeById(id: String): XBreakpointType<*, *>? {
    if (FrontendApplicationInfo.getFrontendType() !is FrontendType.Monolith) return null
    return XBreakpointUtil.findType(id)
  }
}