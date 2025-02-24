// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XDebuggerSessionInfoDto

/**
 * Frontend-side analogue of [com.intellij.xdebugger.XDebuggerManagerListener]
 *
 * The only difference is that [processStarted] accepts an instance of [XDebuggerSessionInfoDto].
 * This object is supposed to store data that is more reasonable to be passed along with the event,
 * instead of being requested from the backend when needed.
 */
internal interface FrontendXDebuggerManagerListener {
  fun processStarted(sessionId: XDebugSessionId, sessionDto: XDebuggerSessionInfoDto) {}
  fun processStopped(sessionId: XDebugSessionId) {}
  fun activeSessionChanged(previousSessionId: XDebugSessionId?, currentSessionId: XDebugSessionId?) {}
}
