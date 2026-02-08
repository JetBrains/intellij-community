// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.shared.performDebuggerAction
import com.intellij.xdebugger.impl.DebuggerSupport

/**
 * Base class for debugger actions.
 *
 * Subclasses should provide the action logic by implementing [getHandler].
 */
abstract class XDebuggerActionBase protected constructor(private val myHideDisabledInPopup: Boolean) : AnAction() {
  protected constructor() : this(false)

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
      return isEnabled(project, e)
    }
    return false
  }

  @Deprecated("Use XDebuggerActionBase#getHandler() instead.")
  protected open fun getHandler(debuggerSupport: DebuggerSupport): DebuggerActionHandler {
    throw AbstractMethodError("XDebuggerActionBase#getHandler() should be implemented.")
  }

  /**
   * Returns the handler that performs the actual action logic for this debugger action.
   * Should be implemented in subclasses.
   */
  @Suppress("DEPRECATION")
  protected open fun getHandler(): DebuggerActionHandler {
    return getHandler(DebuggerSupport())
  }

  private fun isEnabled(project: Project, event: AnActionEvent): Boolean {
    return getHandler().isEnabled(project, event)
  }

  override fun actionPerformed(e: AnActionEvent) {
    performDebuggerAction(e.project, e.dataContext) { performWithHandler(e) }
  }

  protected fun performWithHandler(e: AnActionEvent): Boolean {
    val project: Project? = e.project
    if (project == null || project.isDisposed()) {
      return true
    }
    if (isEnabled(project, e)) {
      perform(project, e)
      return true
    }
    return false
  }

  private fun perform(project: Project, e: AnActionEvent) {
    getHandler().perform(project, e)
  }

  protected open fun isHidden(event: AnActionEvent): Boolean {
    val project: Project? = event.project
    if (project != null && !project.isDisposed()) {
      return getHandler().isHidden(project, event)
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
