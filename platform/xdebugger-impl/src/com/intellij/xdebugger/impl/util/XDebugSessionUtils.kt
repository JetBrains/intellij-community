// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.util

import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun XDebugSession.advise(listener: XDebugSessionListener) {
  addSessionListener(listener)
}

@ApiStatus.Internal
fun XDebugSession.adviseOnFrameChanged(action: (XExecutionStack, XStackFrame) -> Unit) {
  advise(object : XDebugSessionListener {
    override fun stackFrameChanged() {
      val session = (this@adviseOnFrameChanged as? XDebugSessionImpl) ?: return
      val currentExecutionStack = session.currentExecutionStack ?: return
      action(currentExecutionStack, currentStackFrame ?: return)
    }
  })
}

@ApiStatus.Internal
fun XDebugSession.adviseOnSessionPaused(action: () -> Unit) {
  advise(object : XDebugSessionListener {
    override fun sessionPaused() = action()
  })
}

@ApiStatus.Internal
fun XDebugSession.adviseOnSessionResumed(action: () -> Unit) {
  advise(object : XDebugSessionListener {
    override fun sessionResumed() = action()
  })
}

@ApiStatus.Internal
fun XDebugSession.adviseOnSessionStopped(action: () -> Unit) {
  advise(object : XDebugSessionListener {
    override fun sessionStopped() = action()
  })
}

@ApiStatus.Internal
fun XDebugSession.adviseOnBeforeSessionResume(action: () -> Unit) {
  advise(object : XDebugSessionListener {
    override fun beforeSessionResume() = action()
  })
}

@ApiStatus.Internal
fun XDebugSession.adviseOnSettingsChanged(action: () -> Unit) {
  advise(object : XDebugSessionListener {
    override fun settingsChanged() = action()
  })
}
