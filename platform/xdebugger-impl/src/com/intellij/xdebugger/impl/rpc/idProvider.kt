// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy

internal suspend fun <T> withId(value: XValue, session: XDebugSessionProxy, block: suspend (XValueId) -> T): T {
  return XDebugManagerProxy.getInstance().withId(value, session, block)
}
