// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class XStackFrameModel(
  val stackFrame: XStackFrame,
  val session: XDebugSessionImpl,
)

@ApiStatus.Internal
fun XStackFrameId.findValue(): XStackFrameModel? {
  return findValueById(this, type = XStackFrameValueIdType)
}

@ApiStatus.Internal
fun XStackFrame.storeGlobally(session: XDebugSessionImpl): XStackFrameId {
  return storeValueGlobally(session.coroutineScope, XStackFrameModel(this, session), type = XStackFrameValueIdType)
}

@ApiStatus.Internal
object XStackFrameValueIdType : BackendValueIdType<XStackFrameId, XStackFrameModel>(::XStackFrameId)