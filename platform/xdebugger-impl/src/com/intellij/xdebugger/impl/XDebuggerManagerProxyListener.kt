// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.util.messages.Topic
import com.intellij.xdebugger.SplitDebuggerMode
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.impl.proxy.asProxy
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
