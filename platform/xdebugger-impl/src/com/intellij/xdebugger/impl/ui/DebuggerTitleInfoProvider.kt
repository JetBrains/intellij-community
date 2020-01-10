// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.DefaultPartTitle
import com.intellij.openapi.wm.impl.simpleTitleParts.SimpleTitleInfoProvider
import com.intellij.openapi.wm.impl.simpleTitleParts.TitleInfoSubscription
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener

class DebuggerTitleInfoProvider(var project: Project) : SimpleTitleInfoProvider(TitleInfoSubscription.ALWAYS_ACTIVE, TitleInfoSubscription.ALWAYS_ACTIVE) {
  private var subscriptionDisposable: Disposable? = null

  private var debuggerSessionStarted = false
    set(value) {
      if (field == value) return

      field = value
      updateValue()
    }

  override fun updateSubscriptions() {
    if (!isEnabled()) {
      subscriptionDisposable?.let {
        if (!Disposer.isDisposed(it)) it.dispose()
        return
      }
      return
    }

    if (subscriptionDisposable?.let{Disposer.isDisposed(it)} != false) {
      subscriptionDisposable = addSubscription(project)
    }

    super.updateSubscriptions()
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
  override val value: String = "[Debugger]"

}