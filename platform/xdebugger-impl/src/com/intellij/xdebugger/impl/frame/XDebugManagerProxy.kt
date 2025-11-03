// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import com.intellij.platform.debugger.impl.rpc.XValueId
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.XDebuggerExecutionPointManager
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * This is a set of util functions that can be used in the shared code with [XDebugSessionProxy], RPC ID, etc.
 *
 * This manager and all its usages should be moved to the frontend module in the future.
 * For now, it is kept here to avoid breaking the LUXed implementation.
 *
 * @see XDebugSessionProxy
 */
@ApiStatus.Internal
interface XDebugManagerProxy {
  fun isEnabled(): Boolean

  suspend fun <T> withId(stack: XExecutionStack, session: XDebugSessionProxy, block: suspend (XExecutionStackId) -> T): T
  fun getCurrentSessionProxy(project: Project): XDebugSessionProxy?
  fun getCurrentSessionFlow(project: Project): Flow<XDebugSessionProxy?>
  fun getSessions(project: Project): List<XDebugSessionProxy>

  fun getBreakpointManagerProxy(project: Project): XBreakpointManagerProxy

  fun getDebuggerExecutionPointManager(project: Project): XDebuggerExecutionPointManager?

  /**
   * Returns `true` if the given [xValue] is presented on BE.
   * In monolith mode, this method always returns `true`;
   * in split mode, it returns `true` if the given [xValue]
   * has access to ID used to find the relevant backend counterpart.
   */
  fun hasBackendCounterpart(xValue: XValue): Boolean

  /**
   * Invokes the given [block] with the ID of the given [value].
   *
   * Use with care, ensure that [hasBackendCounterpart] returns `true` for the given [value].
   */
  suspend fun <T> withId(value: XValue, session: XDebugSessionProxy, block: suspend (XValueId) -> T): T

  fun findSessionProxy(project: Project, sessionId: XDebugSessionId): XDebugSessionProxy? {
    return getSessions(project).firstOrNull { it.id == sessionId }
  }

  /**
   * Gets ID of the given [value].
   *
   * This method is used in split mode to pass the ID of the value from frontend to backend.
   * It's not supported in monolith mode.
   */
  fun getXValueId(value: XValue): XValueId?

  /**
   * Gets ID of the given [stack].
   *
   * This method is used in split mode to pass the ID of the execution stack from frontend to backend.
   * It's not supported in monolith mode.
   */
  fun getXExecutionStackId(stack: XExecutionStack): XExecutionStackId?

  companion object {
    private val EP_NAME = ExtensionPointName.create<XDebugManagerProxy>("com.intellij.xdebugger.managerProxy")

    @JvmStatic
    fun getInstance(): XDebugManagerProxy = EP_NAME.findFirstSafe { it.isEnabled() } ?: error("No XDebugManagerProxy implementation found")
  }
}
