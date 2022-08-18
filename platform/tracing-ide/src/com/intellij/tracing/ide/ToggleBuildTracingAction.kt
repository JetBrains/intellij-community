// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tracing.ide

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

class ToggleBuildTracingAction : ToggleAction(TracingBundle.message("action.toggle.build.tracing.text")) {
  override fun isSelected(e: AnActionEvent): Boolean {
    return TracingService.getInstance().isTracingEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    TracingService.getInstance().setTracingEnabled(state)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}