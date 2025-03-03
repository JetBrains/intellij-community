// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Used only for Java code, since MutableStateFlow function cannot be called there.
internal fun <T> createMutableStateFlow(initialValue: T): MutableStateFlow<T> {
  return MutableStateFlow(initialValue)
}

internal fun addOnSessionSelectedListener(session: XDebugSessionProxy, action: () -> Unit) {
  val scope = session.coroutineScope
  val sessionId = scope.async {
    session.sessionId()
  }
  session.project.messageBus.connect(scope).subscribe(FrontendXDebuggerManagerListener.TOPIC, object : FrontendXDebuggerManagerListener {
    override fun activeSessionChanged(previousSessionId: XDebugSessionId?, currentSessionId: XDebugSessionId?) {
      scope.launch {
        if (currentSessionId == sessionId.await()) {
          action()
        }
      }
    }
  })
}