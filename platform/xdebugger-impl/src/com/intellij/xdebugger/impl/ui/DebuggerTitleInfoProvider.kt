// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.TitleInfoProvider
import com.intellij.openapi.wm.impl.simpleTitleParts.RegistryOption
import com.intellij.openapi.wm.impl.simpleTitleParts.SimpleTitleInfoProvider
import com.intellij.util.application
import com.intellij.util.ui.EDT
import com.intellij.xdebugger.*
import javax.swing.SwingUtilities

private class DebuggerTitleInfoProvider : SimpleTitleInfoProvider(RegistryOption("ide.debug.in.title", null)) {
  companion object {
    private fun getHelper(project: Project) = project.service<DebuggerTitleInfoProviderService>()
  }

  init {
    option.listener = {
      ProjectManager.getInstance().openProjects.forEach {
        updateSubscriptions(it)
      }
      updateNotify()
    }
  }

  override fun addSubscription(project: Project, disp: Disposable, value: (provider: TitleInfoProvider) -> Unit) {
    super.addSubscription(project, disp, value)
    updateSubscriptions(project)
  }

  override fun isActive(project: Project): Boolean {
    return isEnabled() && getHelper(project).debuggerSessionStarted
  }

  override fun getValue(project: Project): String {
    return if (getHelper(project).debuggerSessionStarted) "[Debugger]" else ""
  }

  private fun updateSubscriptions(project: Project) {
    val helper = getHelper(project)
    helper.checkState(this)

    val disposable = helper.subscriptionDisposable
    if (!isEnabled()) {
      helper.subscriptionDisposable = null
      if (disposable != null) {
        Disposer.dispose(disposable)
      }
      return
    }
    else if (disposable == null) {
      helper.subscriptionDisposable = helper.addSubscription(this)
    }
  }

  @Service
  private class DebuggerTitleInfoProviderService(private val project: Project) {
    var debuggerSessionStarted = false
    var subscriptionDisposable: Disposable? = null

    fun checkState(provider: DebuggerTitleInfoProvider) {
      fun action() {
        application.assertIsDispatchThread()
        debuggerSessionStarted = XDebuggerManager.getInstance(project)?.let {
          provider.isEnabled() && it.debugSessions.isNotEmpty()
        } ?: false
        provider.updateNotify()
      }

      if (EDT.isCurrentThreadEdt()) {
        action()
      } else {
        // Some debuggers are known to terminate their debug sessions outside the EDT (RIDER-66994). Reschedule title update for this case.
        SwingUtilities.invokeLater(::action)
      }
    }

    fun addSubscription(provider: DebuggerTitleInfoProvider): Disposable {
      val disposable = Disposable {
        subscriptionDisposable = null
      }
      Disposer.register(project, disposable)

      project.messageBus.connect(disposable).subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
        override fun processStarted(debugProcess: XDebugProcess) {
          application.assertIsDispatchThread()
          debuggerSessionStarted = true

          debugProcess.session.addSessionListener(object : XDebugSessionListener {
            override fun sessionStopped() {
              checkState(provider)
            }
          })
        }

        override fun processStopped(debugProcess: XDebugProcess) {
          checkState(provider)
        }

        override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
          checkState(provider)
        }
      })
      return disposable
    }
  }
}