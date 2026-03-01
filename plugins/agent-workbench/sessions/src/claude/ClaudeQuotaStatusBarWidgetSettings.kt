// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.claude

import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetSettings
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object ClaudeQuotaStatusBarWidgetSettings {
  private val _enabledFlow = MutableStateFlow(readEnabledFromSettings())
  val enabledFlow: StateFlow<Boolean> = _enabledFlow.asStateFlow()

  fun isEnabled(): Boolean {
    syncEnabledState()
    return _enabledFlow.value
  }

  fun syncEnabledState() {
    _enabledFlow.value = readEnabledFromSettings()
  }

  fun setEnabled(enabled: Boolean) {
    val factory = findFactory() ?: return
    val settings = StatusBarWidgetSettings.getInstance()
    if (settings.isEnabled(factory) == enabled) {
      return
    }

    settings.setEnabled(factory, enabled)
    for (project in ProjectManager.getInstance().openProjects) {
      project.service<StatusBarWidgetsManager>().updateWidget(factory)
    }
    _enabledFlow.value = enabled
  }

}

private fun readEnabledFromSettings(): Boolean {
  val factory = findFactory() ?: return false
  return StatusBarWidgetSettings.getInstance().isEnabled(factory)
}

private fun findFactory(): StatusBarWidgetFactory? {
  return StatusBarWidgetFactory.EP_NAME.extensionList.firstOrNull { it.id == CLAUDE_QUOTA_WIDGET_ID }
}
