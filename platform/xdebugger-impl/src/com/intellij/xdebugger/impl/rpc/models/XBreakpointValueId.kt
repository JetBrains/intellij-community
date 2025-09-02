// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun XBreakpointId.findValue(): XBreakpointBase<*, *, *>? {
  return findValueById(this, type = XDebuggerBreakpointValueIdType)
}

@ApiStatus.Internal
fun XBreakpointBase<*, *, *>.storeGlobally(coroutineScope: CoroutineScope): XBreakpointId {
  return storeValueGlobally(coroutineScope, this, type = XDebuggerBreakpointValueIdType)
}


@ApiStatus.Internal
private object XDebuggerBreakpointValueIdType : BackendValueIdType<XBreakpointId, XBreakpointBase<*, *, *>>(::XBreakpointId)