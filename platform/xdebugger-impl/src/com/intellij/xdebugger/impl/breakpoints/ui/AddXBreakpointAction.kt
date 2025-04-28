// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.impl.breakpoints.XBreakpointTypeProxy
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AddXBreakpointAction(
  private val project: Project,
  private val myType: XBreakpointTypeProxy,
  private val saveCurrentItem: () -> Unit,
  private val selectBreakpoint: (breakpointId: XBreakpointId) -> Unit,
) : AnAction(), DumbAware {

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.icon = myType.enabledIcon
    e.presentation.text = myType.title
  }

  override fun actionPerformed(e: AnActionEvent) {
    saveCurrentItem()
    project.service<AddXBreakpointActionCoroutineScope>().cs.launch {
      val breakpoint = myType.addBreakpoint(project)
      if (breakpoint != null) {
        withContext(Dispatchers.EDT) {
          selectBreakpoint(breakpoint.id)
        }
      }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}

@Service(Service.Level.PROJECT)
private class AddXBreakpointActionCoroutineScope(val cs: CoroutineScope)