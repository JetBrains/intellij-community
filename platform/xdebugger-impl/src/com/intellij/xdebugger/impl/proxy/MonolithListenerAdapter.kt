// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.proxy

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.impl.XDebuggerManagerProxyListener

private class MonolithListenerAdapter : XDebuggerManagerListener {
  private val shouldTriggerListener: Boolean
    get() {
      val frontendType = FrontendApplicationInfo.getFrontendType()
      return !SplitDebuggerMode.isSplitDebugger() && frontendType is FrontendType.Monolith
    }

  override fun processStarted(debugProcess: XDebugProcess) {
    if (!shouldTriggerListener) return
    val session = debugProcess.session
    session.project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).sessionStarted(session.asProxy())
  }

  override fun processStopped(debugProcess: XDebugProcess) {
    if (!shouldTriggerListener) return
    val session = debugProcess.session
    session.project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).sessionStopped(session.asProxy())
  }

  override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
    if (!shouldTriggerListener) return
    val project = previousSession?.project ?: currentSession?.project ?: return
    project.messageBus.syncPublisher(XDebuggerManagerProxyListener.TOPIC).activeSessionChanged(previousSession?.asProxy(),
                                                                                               currentSession?.asProxy())
  }
}
