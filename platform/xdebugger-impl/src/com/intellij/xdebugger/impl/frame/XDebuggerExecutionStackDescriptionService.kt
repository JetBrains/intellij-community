// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.XExecutionStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * A service for generating a description of an execution stack in the debugger.
 * Works only for Side-by-Side mode now
 */
@ApiStatus.Internal
@ApiStatus.Experimental
open class XDebuggerExecutionStackDescriptionService(private val coroutineScope: CoroutineScope) {

  @Nls
  protected open suspend fun doGetExecutionStackDescription(stack: XExecutionStack, session: XDebugSession): String = throw IllegalStateException("Not supposed to call this method")

  @Nls
  fun getExecutionStackDescription(stack: XExecutionStack, session: XDebugSession): Deferred<String> {
    return coroutineScope.async(Dispatchers.Default) {
      doGetExecutionStackDescription(stack, session)
    }
  }

  open fun isAvailable(): Boolean = false
}