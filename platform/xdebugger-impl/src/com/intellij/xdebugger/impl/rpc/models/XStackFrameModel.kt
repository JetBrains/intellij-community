// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ConsistentCopyVisibility
@ApiStatus.Internal
data class XStackFrameModel internal constructor(
  val coroutineScope: CoroutineScope,
  val stackFrame: XStackFrame,
  val session: XDebugSessionImpl,
)

@ApiStatus.Internal
fun XStackFrameId.findValue(): XStackFrameModel? {
  return findValueById(this, type = XStackFrameValueIdType)
}

@ApiStatus.Internal
fun XStackFrame.storeGlobally(coroutineScope: CoroutineScope, session: XDebugSessionImpl): XStackFrameId {
  return storeValueGlobally(coroutineScope, XStackFrameModel(coroutineScope, this, session), type = XStackFrameValueIdType)
}

private object XStackFrameValueIdType : BackendValueIdType<XStackFrameId, XStackFrameModel>(::XStackFrameId)