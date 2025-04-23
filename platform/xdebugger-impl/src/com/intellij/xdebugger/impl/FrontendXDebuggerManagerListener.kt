// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.util.messages.Topic
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import org.jetbrains.annotations.ApiStatus

/**
 * Frontend-side analogue of [com.intellij.xdebugger.XDebuggerManagerListener]
 */
@ApiStatus.Internal
interface FrontendXDebuggerManagerListener {
  fun sessionStarted(session: XDebugSessionProxy) {}
  fun sessionStopped(session: XDebugSessionProxy) {}
  fun activeSessionChanged(previousSession: XDebugSessionProxy?, currentSession: XDebugSessionProxy?) {}

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<FrontendXDebuggerManagerListener> =
      Topic("FrontendXDebuggerManager events", FrontendXDebuggerManagerListener::class.java)
  }
}
