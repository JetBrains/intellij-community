// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XDebuggerEntityIdProvider {
  fun isEnabled(): Boolean
  suspend fun <T> withId(value: XValue, session: XDebugSessionProxy, block: suspend (XValueId) -> T): T

  companion object {
    private val EP_NAME = ExtensionPointName<XDebuggerEntityIdProvider>("com.intellij.xdebugger.entityIdProvider")
    internal val provider: XDebuggerEntityIdProvider get() = EP_NAME.extensionList.first { it.isEnabled() }
  }
}

internal suspend fun <T> withId(value: XValue, session: XDebugSessionProxy, block: suspend (XValueId) -> T): T {
  return XDebuggerEntityIdProvider.provider.withId(value, session, block)
}
