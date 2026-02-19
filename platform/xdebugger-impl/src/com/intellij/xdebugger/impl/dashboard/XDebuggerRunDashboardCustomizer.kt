// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.dashboard

import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.dashboard.RunDashboardCustomizationBuilder
import com.intellij.execution.dashboard.RunDashboardCustomizer
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.xdebugger.XDebuggerManager

internal class XDebuggerRunDashboardCustomizer : RunDashboardCustomizer() {
  override fun isApplicable(settings: RunnerAndConfigurationSettings, descriptor: RunContentDescriptor?): Boolean {
    if (descriptor == null) return false
    val executors = ExecutionManager.getInstance(settings.configuration.project).getExecutors(descriptor)
    return executors.any { it.id == DefaultDebugExecutor.EXECUTOR_ID }
  }

  override fun updatePresentation(customizationBuilder: RunDashboardCustomizationBuilder, configurationSettings: RunnerAndConfigurationSettings, descriptor: RunContentDescriptor?): Boolean {
    val processHandler = descriptor?.processHandler ?: return false
    val session = XDebuggerManager.getInstance(configurationSettings.configuration.project).debugSessions.find { it.debugProcess.processHandler === processHandler }
                  ?: return false
    if (session.isPaused) {
      customizationBuilder.setIcon(AllIcons.Actions.Pause)
    } else if (!session.isStopped) {
      customizationBuilder.setIcon(AllIcons.Actions.StartDebugger)
    }
    return false
  }
}