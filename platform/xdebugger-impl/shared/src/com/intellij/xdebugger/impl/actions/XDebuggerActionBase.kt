// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.ui.performDebuggerActionAsync
import com.intellij.xdebugger.impl.DebuggerSupport

abstract class XDebuggerActionBase protected constructor(private val myHideDisabledInPopup: Boolean = false) : AnAction() {
  override fun update(event: AnActionEvent) {
    val presentation: Presentation = event.presentation
    val hidden = isHidden(event)
    if (hidden) {
      presentation.setEnabledAndVisible(false)
      return
    }

    val enabled = isEnabled(event)
    if (myHideDisabledInPopup && event.isFromContextMenu) {
      presentation.setVisible(enabled)
    }
    else {
      presentation.setVisible(true)
    }
    presentation.setEnabled(enabled)
  }

  protected open fun isEnabled(e: AnActionEvent): Boolean {
    val project: Project? = e.project
    if (project != null && !project.isDisposed()) {
      val support: DebuggerSupport = DebuggerSupport()
      if (isEnabled(project, e, support)) {
        return true
      }
      return false
    }
    return false
  }

  protected abstract fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler

  private fun isEnabled(project: Project, event: AnActionEvent, support: DebuggerSupport): Boolean {
    return getHandler(support).isEnabled(project, event)
  }

  override fun actionPerformed(e: AnActionEvent) {
    performDebuggerActionAsync(e) { performWithHandler(e) }
  }

  protected fun performWithHandler(e: AnActionEvent): Boolean {
    val project: Project? = e.project
    if (project == null || project.isDisposed()) {
      return true
    }

    val support: DebuggerSupport = DebuggerSupport()
    if (isEnabled(project, e, support)) {
      perform(project, e, support)
      return true
    }
    return false
  }

  private fun perform(
    project: Project,
    e: AnActionEvent,
    support: DebuggerSupport
  ) {
    getHandler(support).perform(project, e)
  }

  protected open fun isHidden(event: AnActionEvent): Boolean {
    val project: Project? = event.project
    if (project != null && !project.isDisposed()) {
      val support: DebuggerSupport = DebuggerSupport()
      return getHandler(support).isHidden(project, event)
    }
    return true
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun isDumbAware(): Boolean {
    return true
  }
}
