// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.engine.ui

import com.intellij.agent.workbench.engine.platform.EngineProjectService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

/**
 * Preview helper: starts a mock Engine thread so it appears in the existing tool window under the
 * Engine provider. Stand-in until real ACP/remote runtimes drive the Engine.
 */
internal class RunMockThreadAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    EngineProjectService.getInstance(project)
      .startMockThread("Demo: refactor the authentication module and add tests")
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
