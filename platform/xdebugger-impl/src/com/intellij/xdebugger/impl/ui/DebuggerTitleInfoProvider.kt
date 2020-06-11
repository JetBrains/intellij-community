// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.simpleTitleParts.RegistryOption
import com.intellij.openapi.wm.impl.simpleTitleParts.SimpleTitleInfoProvider
import com.intellij.xdebugger.*

class DebuggerTitleInfoProvider(var project: Project) : SimpleTitleInfoProvider(RegistryOption("ide.debug.in.title", project)) {
  private var subscriptionDisposable: Disposable? = null

  private var debuggerSessionStarted = false

  override fun updateSubscriptions() {
    checkState()

    if (!isEnabled()) {
      subscriptionDisposable?.dispose()
      subscriptionDisposable = null
      return
    }

    if (subscriptionDisposable == null) {
      subscriptionDisposable = addSubscription(project)
    }

    super.updateSubscriptions()
  }

  private fun addSubscription(baseDisposable: Disposable): Disposable {
    val connection = project.messageBus.connect()
    connection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
      override fun processStarted(debugProcess: XDebugProcess) {
        debuggerSessionStarted = true

        debugProcess.session.addSessionListener(object : XDebugSessionListener {
          override fun sessionStopped() {
            checkState()
          }
        })
      }

      override fun processStopped(debugProcess: XDebugProcess) {
        checkState()
      }

      override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
        checkState()
      }
    })

    val dsp = Disposable {
      connection.disconnect()
      subscriptionDisposable = null
    }
    Disposer.register(baseDisposable, dsp)
    return dsp
  }

  private fun checkState() {
    debuggerSessionStarted = isEnabled() && XDebuggerManager.getInstance(project).debugSessions.isNotEmpty()
    updateValue()
  }

  override val isActive: Boolean
    get() = super.isActive && debuggerSessionStarted
  override val value: String
    get() = if(debuggerSessionStarted) "[Debugger]" else ""
}