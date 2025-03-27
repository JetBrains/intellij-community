// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class XExecutionStackModel(
  val executionStack: XExecutionStack,
  val session: XDebugSessionImpl,
)

@ApiStatus.Internal
fun XExecutionStackId.findValue(): XExecutionStackModel? {
  return findValueById(this, type = XExecutionStackValueIdType)
}

@ApiStatus.Internal
fun XExecutionStack.storeValueGlobally(session: XDebugSessionImpl): XExecutionStackId {
  return storeValueGlobally(session.coroutineScope, XExecutionStackModel(this, session), type = XExecutionStackValueIdType)
}

@ApiStatus.Internal
object XExecutionStackValueIdType : BackendValueIdType<XExecutionStackId, XExecutionStackModel>(::XExecutionStackId)