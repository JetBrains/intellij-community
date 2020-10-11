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
import com.intellij.xdebugger.*

private class DebuggerTitleInfoProvider : TitleInfoProvider {
  companion object {
    private fun getHelper(project: Project) = project.service<DebuggerTitleInfoProviderService>()
  }

  private val option = RegistryOption("ide.debug.in.title", null)

  init {
    option.listener = {
      ProjectManager.getInstance().openProjects.forEach {
        updateSubscriptions(it)
      }
      updateNotify()
    }
  }

  private var updateListeners: MutableSet<((provider: TitleInfoProvider) -> Unit)> = HashSet()

  override val borderlessSuffix: String = ""
  override val borderlessPrefix: String = " "

  override fun addUpdateListener(project: Project, value: (provider: TitleInfoProvider) -> Unit) {
    updateListeners.add(value)
    updateSubscriptions(project)
    updateNotify()
  }

  private fun isEnabled(): Boolean {
    return option.isActive && updateListeners.isNotEmpty()
  }

  private fun updateNotify() {
    updateListeners.forEach { it(this) }
    TitleInfoProvider.fireConfigurationChanged()
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
      debuggerSessionStarted = provider.isEnabled() && XDebuggerManager.getInstance(project).debugSessions.isNotEmpty()
      provider.updateNotify()
    }

    fun addSubscription(provider: DebuggerTitleInfoProvider): Disposable {
      val disposable = Disposable {
        subscriptionDisposable = null
      }
      Disposer.register(project, disposable)

      project.messageBus.connect(disposable).subscribe(XDebuggerManager.TOPIC, object : XDebuggerManagerListener {
        override fun processStarted(debugProcess: XDebugProcess) {
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