// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import com.intellij.platform.debugger.impl.rpc.XValueId
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XDebuggerMonolithAccessPoint {
  fun getSession(proxy: XDebugSessionProxy): XDebugSession?
  fun getSessionNonSplitOnly(proxy: XDebugSessionProxy): XDebugSession?
  fun asProxy(session: XDebugSession): XDebugSessionProxy?
  fun getValue(valueId: XValueId): XValue?
  fun getExecutionStack(stackId: XExecutionStackId): XExecutionStack?
  fun getBreakpointType(typeId: String): XBreakpointType<*, *>?
  fun getBreakpoint(breakpointId: XBreakpointId): XBreakpoint<*>?
  fun getBreakpointId(breakpoint: XBreakpoint<*>): XBreakpointId?
  fun asProxy(breakpoint: XBreakpoint<*>): XBreakpointProxy?

  /**
   * Creates a temporary [XValueId] for the given [XValue] in the given session, calls [block] with it,
   * and disposes the temporary model after [block] completes.
   */
  suspend fun <T> withTemporaryXValueId(value: XValue, proxy: XDebugSessionProxy, block: suspend (XValueId) -> T): T

  companion object {
    internal val EP_NAME = ExtensionPointName<XDebuggerMonolithAccessPoint>("com.intellij.xdebugger.monolithAccessPoint")

    fun <T> find(block: (XDebuggerMonolithAccessPoint) -> T): T? {
      return EP_NAME.computeSafeIfAny(block)
    }

    fun findFirst(): XDebuggerMonolithAccessPoint? {
      return EP_NAME.extensionList.firstOrNull()
    }
  }
}