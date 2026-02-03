// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.ui

import com.intellij.platform.debugger.impl.shared.XDebuggerMonolithAccessPoint
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
object XDebuggerEntityConverter {
  /**
   * For a given [XDebugSessionProxy] finds the corresponding [XDebugSession] instance.
   *
   * Always returns `null` on the frontend.
   *
   * Use this method to implement monolith-only features with a split debugger enabled.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun getSession(proxy: XDebugSessionProxy): XDebugSession? {
    return XDebuggerMonolithAccessPoint.find { it.getSession(proxy) }
  }

  /**
   * For a given [XDebugSessionProxy] finds the corresponding [XDebugSession] instance only if split debugger is disabled.
   *
   * Use this method to keep the exact same behavior in monolith and remdev when a split debugger is enabled.
   */
  @ApiStatus.Internal
  @JvmStatic
  fun getSessionNonSplitOnly(proxy: XDebugSessionProxy): XDebugSession? {
    return XDebuggerMonolithAccessPoint.find { it.getSessionNonSplitOnly(proxy) }
  }

  @ApiStatus.Internal
  @JvmStatic
  fun asProxy(session: XDebugSession): XDebugSessionProxy {
    return XDebuggerMonolithAccessPoint.find { it.asProxy(session) }
           ?: error("No XDebuggerMonolithAccessPoint implementation that can convert $session found. " +
                    "XDebuggerMonolithAccessPointImpl should be registered in a shared module and always be able to convert sessions.")
  }
}