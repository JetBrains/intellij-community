// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XSuspendContextId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class XSuspendContextModel(
  val suspendContext: XSuspendContext,
  val session: XDebugSessionImpl,
)

@ApiStatus.Internal
fun XSuspendContextId.findValue(): XSuspendContextModel? {
  return findValueById(this, type = XSuspendContextValueIdType)
}

@ApiStatus.Internal
object XSuspendContextValueIdType : BackendValueIdType<XSuspendContextId, XSuspendContextModel>(::XSuspendContextId)