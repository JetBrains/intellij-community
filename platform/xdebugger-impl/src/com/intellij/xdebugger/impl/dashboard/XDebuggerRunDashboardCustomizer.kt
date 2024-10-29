// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.dashboard

import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.dashboard.RunDashboardCustomizer
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.xdebugger.XDebuggerManager

internal class XDebuggerRunDashboardCustomizer : RunDashboardCustomizer() {
  override fun isApplicable(settings: RunnerAndConfigurationSettings, descriptor: RunContentDescriptor?): Boolean {
    if (descriptor == null) return false
    val executors = ExecutionManager.getInstance(settings.configuration.project).getExecutors(descriptor)
    return executors.any { it.id == DefaultDebugExecutor.EXECUTOR_ID }
  }

  override fun updatePresentation(presentation: PresentationData, node: RunDashboardRunConfigurationNode): Boolean {
    val processHandler = node.descriptor?.processHandler ?: return false
    val session = XDebuggerManager.getInstance(node.project).debugSessions.find { it.debugProcess.processHandler === processHandler }
                  ?: return false
    if (session.isPaused) {
      presentation.setIcon(AllIcons.Actions.Pause)
    }
    return false
  }
}