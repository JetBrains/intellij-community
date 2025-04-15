// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.findValueById
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun XDebugSessionId.findValue(): XDebugSessionImpl? {
  return findValueById(this, type = XDebugSessionValueIdType)
}

internal object XDebugSessionValueIdType : BackendValueIdType<XDebugSessionId, XDebugSessionImpl>(::XDebugSessionId)