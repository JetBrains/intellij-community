// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.util.messages.Topic
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import org.jetbrains.annotations.ApiStatus

/**
 * [XDebugSessionProxy] analogue of [com.intellij.xdebugger.XDebuggerManagerListener]
 */
@ApiStatus.Internal
interface XDebuggerManagerProxyListener {
  fun sessionStarted(session: XDebugSessionProxy) {}
  fun sessionStopped(session: XDebugSessionProxy) {}
  fun activeSessionChanged(previousSession: XDebugSessionProxy?, currentSession: XDebugSessionProxy?) {}

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<XDebuggerManagerProxyListener> =
      Topic("XDebuggerManagerListener proxy events", XDebuggerManagerProxyListener::class.java)
  }
}
