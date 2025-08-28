// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XExecutionStackId
import com.intellij.xdebugger.impl.rpc.XValueId
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
  suspend fun <T> withId(value: XValue, session: XDebugSessionProxy, block: suspend (XValueId) -> T): T
  suspend fun <T> withId(value: XExecutionStack, session: XDebugSessionProxy, block: suspend (XExecutionStackId) -> T): T
  fun getCurrentSessionProxy(project: Project): XDebugSessionProxy?
  fun getSessionIdByContentDescriptor(project: Project, descriptor: RunContentDescriptor): XDebugSessionId?
  fun getCurrentSessionFlow(project: Project): Flow<XDebugSessionProxy?>
  fun getSessions(project: Project): List<XDebugSessionProxy>

  fun getBreakpointManagerProxy(project: Project): XBreakpointManagerProxy

  /**
   * Returns `true` if the given [xValue] is presented on BE.
   * In monolith mode, this method always returns `true`;
   * in split mode, it returns `true` if the given [xValue]
   * has an access to ID used to find the relevant backend counterpart.
   */
  fun hasBackendCounterpart(xValue: XValue): Boolean

  fun findSessionProxy(project: Project, sessionId: XDebugSessionId): XDebugSessionProxy? {
    return getSessions(project).firstOrNull { it.id == sessionId }
  }

  companion object {
    private val EP_NAME = ExtensionPointName.create<XDebugManagerProxy>("com.intellij.xdebugger.managerProxy")

    @JvmStatic
    fun getInstance(): XDebugManagerProxy = EP_NAME.findFirstSafe { it.isEnabled() } ?: error("No XDebugManagerProxy implementation found")
  }
}
