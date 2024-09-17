// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.dashboard

import com.intellij.execution.ExecutionListener
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.dashboard.RunDashboardListener
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.impl.ExecutionManagerImpl.Companion.getDelegatedRunProfile
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager

internal class XDebuggerRunDashboardUpdater : ExecutionListener {
  override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
    if (DefaultDebugExecutor.EXECUTOR_ID != executorId) return

    val settings = env.runnerAndConfigurationSettings ?: return
    val configuration = settings.configuration
    val profile = getDelegatedRunProfile(configuration) ?: configuration
    if (profile !is RunConfiguration) return

    val project = profile.project
    if (!RunDashboardManager.getInstance(project).isShowInDashboard(profile)) return

    val session = XDebuggerManager.getInstance(project).debugSessions.find { it.debugProcess.processHandler === handler }
                  ?: return

    session.addSessionListener(object : XDebugSessionListener {
      override fun sessionPaused() {
        updateDashboard()
      }

      override fun sessionResumed() {
        updateDashboard()
      }

      override fun sessionStopped() {
        session.removeSessionListener(this)
      }

      private fun updateDashboard() {
        project.messageBus.syncPublisher<RunDashboardListener>(RunDashboardManager.DASHBOARD_TOPIC).configurationChanged(profile, false)
      }
    })
  }
}