// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.proxy

import com.intellij.platform.debugger.impl.shared.XDebuggerMonolithAccessPoint
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.rpc.models.findValue

internal class XDebuggerMonolithAccessPointImpl : XDebuggerMonolithAccessPoint {
  override fun getSession(proxy: XDebugSessionProxy): XDebugSession? {
    return proxy.id.findValue()
  }

  override fun getSessionNonSplitOnly(proxy: XDebugSessionProxy): XDebugSession? {
    return (proxy as? MonolithSessionProxy)?.session
  }

  override fun asProxy(session: XDebugSession): XDebugSessionProxy {
    return session.asProxy()
  }
}