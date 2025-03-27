// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XExecutionStackId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ConsistentCopyVisibility
@ApiStatus.Internal
data class XExecutionStackModel internal constructor(
  val coroutineScope: CoroutineScope,
  val executionStack: XExecutionStack,
  val session: XDebugSessionImpl,
)

@ApiStatus.Internal
fun XExecutionStackId.findValue(): XExecutionStackModel? {
  return findValueById(this, type = XExecutionStackValueIdType)
}

@ApiStatus.Internal
fun XExecutionStack.storeGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XExecutionStackId {
  return storeValueGlobally(coroutineScope, XExecutionStackModel(coroutineScope, this, session), type = XExecutionStackValueIdType)
}

private object XExecutionStackValueIdType : BackendValueIdType<XExecutionStackId, XExecutionStackModel>(::XExecutionStackId)