// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.rpc.XDebuggerEntityIdProvider
import com.intellij.xdebugger.impl.rpc.XValueId

private class FrontendXDebuggerEntityIdProvider : XDebuggerEntityIdProvider {
  override fun isEnabled(): Boolean = XDebugSessionProxy.useFeProxy()
  override suspend fun <T> withId(value: XValue, session: XDebugSessionProxy, block: suspend (XValueId) -> T): T {
    val valueId = (value as FrontendXValue).xValueDto.id
    return block(valueId)
  }
}
