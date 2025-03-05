// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive

internal object XDebuggerSuspendScopeProvider {

  /**
   * Provides a [CoroutineScope] whose lifetime is restricted to a current [session] state.
   * If the state of the session changes, the scope is canceled.
   *
   * Note that the state may change for many reasons. For instance:
   * - session is resumed (including stepping)
   * - session is terminated
   * - user switched to another stack frame
   * - user switched to another thread
   * - user evaluated an expression or added a watch
   */
  fun provideSuspendScope(session: XDebugSession): CoroutineScope? {
    require(session is XDebugSessionImpl) {
      "XDebugSessionImpl is expected to be the only direct subclass of XDebugSession, got ${session.javaClass.name}"
    }
    val scope = session.coroutineScope.childScope("XDebuggerSuspendScopeProvider: ${session.sessionName}")
    if (!scope.isActive) {
      thisLogger().info("Can't provide scope for the session: ${session.sessionName} -- parent scope is canceled")
      return null
    }
    session.addSessionListener(object : XDebugSessionListener {
      override fun sessionResumed() {
        clear()
      }

      override fun sessionStopped() {
        clear()
      }

      override fun sessionPaused() {
        clear()
      }

      override fun stackFrameChanged() {
        clear()
      }

      override fun beforeSessionResume() {
        clear()
      }

      override fun settingsChanged() {
        clear()
      }

      private fun clear() {
        session.removeSessionListener(this)
        scope.cancel()
      }
    })
    return scope
  }
}
