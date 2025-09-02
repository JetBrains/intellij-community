// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
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
  fun getCurrentSessionProxy(project: Project): XDebugSessionProxy?
  fun getSessionIdByContentDescriptor(project: Project, descriptor: RunContentDescriptor): XDebugSessionId?
  fun getCurrentSessionFlow(project: Project): Flow<XDebugSessionProxy?>
  fun getSessions(project: Project): List<XDebugSessionProxy>

  fun getBreakpointManagerProxy(project: Project): XBreakpointManagerProxy

  fun canUpdateInlineDebuggerFrames(): Boolean

  companion object {
    private val EP_NAME = ExtensionPointName.create<XDebugManagerProxy>("com.intellij.xdebugger.managerProxy")

    @JvmStatic
    fun getInstance(): XDebugManagerProxy = EP_NAME.findFirstSafe { it.isEnabled() } ?: error("No XDebugManagerProxy implementation found")
  }
}
