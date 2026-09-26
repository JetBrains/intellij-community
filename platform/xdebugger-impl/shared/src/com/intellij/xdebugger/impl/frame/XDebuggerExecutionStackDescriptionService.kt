// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.Disposable
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.frame.XExecutionStack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * A service for generating a description of an execution stack in the debugger.
 * Works only for Side-by-Side mode now
 */
@ApiStatus.Internal
@ApiStatus.Experimental
open class XDebuggerExecutionStackDescriptionService() {

  protected open suspend fun doGetExecutionStackDescription(stack: XExecutionStack, sessionProxy: XDebugSessionProxy, forceReevaluation: Boolean): XDebuggerExecutionStackDescription = throw IllegalStateException("Not supposed to call this method")

  @Nls
  fun getExecutionStackDescription(stack: XExecutionStack, sessionProxy: XDebugSessionProxy, forceReevaluation: Boolean = false): Deferred<XDebuggerExecutionStackDescription> {
    val currentSuspendContextCoroutineScope = sessionProxy.currentSuspendContextCoroutineScope ?: throw CancellationException()
    return currentSuspendContextCoroutineScope.async(Dispatchers.Default) {
      doGetExecutionStackDescription(stack, sessionProxy, forceReevaluation)
    }
  }

  open fun getLoadDescriptionComponent(sessionProxy: XDebugSessionProxy, viewDisposable: Disposable) : XDebuggerDescriptionComponentProvider? = null

  open fun isAvailable(): Boolean = false
}

@ApiStatus.Internal
@ApiStatus.Experimental
@Serializable
data class XDebuggerExecutionStackDescription(
  @SerialName("ShortDescription") @param:Nls val shortDescription: String,
  @SerialName("LongDescription") @param:Nls val longDescription: String
)