// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.util

import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.XDebugSessionImpl

fun XDebugSession.advise(listener: XDebugSessionListener) {
  addSessionListener(listener)
}

fun XDebugSession.adviseOnFrameChanged(action: (XExecutionStack, XStackFrame) -> Unit) {
  advise(object : XDebugSessionListener {
    override fun stackFrameChanged() {
      val session = (this@adviseOnFrameChanged as? XDebugSessionImpl) ?: return
      val currentExecutionStack = session.currentExecutionStack ?: return
      action(currentExecutionStack, currentStackFrame ?: return)
    }
  })
}

fun XDebugSession.adviseOnSessionPaused(action: () -> Unit) {
  advise(object : XDebugSessionListener {
    override fun sessionPaused() = action()
  })
}

fun XDebugSession.adviseOnSessionResumed(action: () -> Unit) {
  advise(object : XDebugSessionListener {
    override fun sessionResumed() = action()
  })
}

fun XDebugSession.adviseOnSessionStopped(action: () -> Unit) {
  advise(object : XDebugSessionListener {
    override fun sessionStopped() = action()
  })
}

fun XDebugSession.adviseOnBeforeSessionResume(action: () -> Unit) {
  advise(object : XDebugSessionListener {
    override fun beforeSessionResume() = action()
  })
}

fun XDebugSession.adviseOnSettingsChanged(action: () -> Unit) {
  advise(object : XDebugSessionListener {
    override fun settingsChanged() = action()
  })
}
