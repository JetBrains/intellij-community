// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XDebuggerMonolithAccessPoint {
  fun getSession(proxy: XDebugSessionProxy): XDebugSession?
  fun getSessionNonSplitOnly(proxy: XDebugSessionProxy): XDebugSession?
  fun asProxy(session: XDebugSession): XDebugSessionProxy?

  companion object {
    internal val EP_NAME = ExtensionPointName<XDebuggerMonolithAccessPoint>("com.intellij.xdebugger.monolithAccessPoint")

    fun <T> find(block: (XDebuggerMonolithAccessPoint) -> T): T? {
      return EP_NAME.computeSafeIfAny(block)
    }
  }
}