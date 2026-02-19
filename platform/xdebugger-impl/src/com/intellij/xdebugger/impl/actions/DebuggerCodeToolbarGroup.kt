// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.util.registry.Registry
import com.intellij.xdebugger.XDebuggerManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DebuggerCodeToolbarGroup : DefaultActionGroup() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null
                                         && Registry.`is`("code.toolbar.debugger.actions")
                                         && XDebuggerManager.getInstance(project).getCurrentSession() != null
  }
}