// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.util

import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun XDebugSession.notifyOnFrameChanged(action: (XExecutionStack, XStackFrame) -> Unit) {
  addSessionListener(object : XDebugSessionListener {
    override fun stackFrameChanged() {
      val session = (this@notifyOnFrameChanged as? XDebugSessionImpl) ?: return
      val currentExecutionStack = session.currentExecutionStack ?: return
      action(currentExecutionStack, currentStackFrame ?: return)
    }
  })
}

@ApiStatus.Internal
fun XDebugSession.notifyOnSessionPaused(action: () -> Unit) {
  addSessionListener(object : XDebugSessionListener {
    override fun sessionPaused() = action()
  })
}

@ApiStatus.Internal
fun XDebugSession.notifyOnSessionResumed(action: () -> Unit) {
  addSessionListener(object : XDebugSessionListener {
    override fun sessionResumed() = action()
  })
}

@ApiStatus.Internal
fun XDebugSession.notifyOnSessionStopped(action: () -> Unit) {
  addSessionListener(object : XDebugSessionListener {
    override fun sessionStopped() = action()
  })
}

@ApiStatus.Internal
fun XDebugSession.notifyOnBeforeSessionResume(action: () -> Unit) {
  addSessionListener(object : XDebugSessionListener {
    override fun beforeSessionResume() = action()
  })
}

@ApiStatus.Internal
fun XDebugSession.notifyOnSettingsChanged(action: () -> Unit) {
  addSessionListener(object : XDebugSessionListener {
    override fun settingsChanged() = action()
  })
}
