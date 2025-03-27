// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XSuspendContextId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ConsistentCopyVisibility
@ApiStatus.Internal
data class XSuspendContextModel internal constructor(
  val coroutineScope: CoroutineScope,
  val suspendContext: XSuspendContext,
  val session: XDebugSessionImpl,
)

@ApiStatus.Internal
fun XSuspendContextId.findValue(): XSuspendContextModel? {
  return findValueById(this, type = XSuspendContextValueIdType)
}

@ApiStatus.Internal
fun XSuspendContext.storeGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XSuspendContextId {
  return storeValueGlobally(coroutineScope, XSuspendContextModel(coroutineScope, this@storeGlobally, session), type = XSuspendContextValueIdType)
}

private object XSuspendContextValueIdType : BackendValueIdType<XSuspendContextId, XSuspendContextModel>(::XSuspendContextId)