// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.constraints.isDisposed
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.DefaultPartTitle
import com.intellij.openapi.wm.impl.simpleTitleParts.SimpleTitleInfoProvider
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener

class DebuggerTitleInfoProvider(project: Project) : SimpleTitleInfoProvider(project) {
  private var subscriptionDisposable: Disposable? = null

  private var debuggerSessionStarted = false
    set(value) {
      if (field == value) return

      field = value
      updateValue()
    }

  override fun updateSubscriptions() {
    super.updateSubscriptions()

    if (!enabled) {
      subscriptionDisposable?.let {
        if (!it.isDisposed) it.dispose()
        return
      }
    }

    if (subscriptionDisposable == null || subscriptionDisposable?.isDisposed == true) {
      subscriptionDisposable = addSubscription(project)
    }
  }

  private fun addSubscription(baseDisposable: Disposable): Disposable {
    val connection = project.messageBus.connect()
    connection.subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
      override fun processStarted(debugProcess: XDebugProcess) {
        debuggerSessionStarted = true
      }

      override fun processStopped(debugProcess: XDebugProcess) {
        debuggerSessionStarted = false
      }

      override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
        debuggerSessionStarted = currentSession != null
      }
    })

    val dsp = Disposable {
      connection.disconnect()
    }
    Disposer.register(baseDisposable, dsp)
    return dsp
  }

  override val isActive: Boolean
    get() = super.isActive && debuggerSessionStarted
  override val borderlessTitlePart: DefaultPartTitle = DefaultPartTitle(" ")
  override val defaultRegistryKey: String? = null
  override val borderlessRegistryKey: String? = null
  override val value: String = "[Debugger]"

}