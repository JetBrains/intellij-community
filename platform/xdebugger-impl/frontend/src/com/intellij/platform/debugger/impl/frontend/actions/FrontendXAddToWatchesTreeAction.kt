// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.xdebugger.impl.actions.areFrontendDebuggerActionsEnabled
import com.intellij.xdebugger.impl.rpc.XDebuggerWatchesApi
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Frontend version of [com.intellij.xdebugger.impl.ui.tree.actions.XAddToWatchesTreeAction]
 */
private class FrontendXAddToWatchesTreeAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    if (!areFrontendDebuggerActionsEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    // TODO[IJPL-175634]: check for availability
    //  see XAddToWatchesTreeAction and DebuggerUIUtil.getWatchesView(e)

    val frontendNode = XDebuggerTreeActionBase.getSelectedNode(e.dataContext)?.valueContainer as? FrontendXValue
    if (frontendNode == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val frontendNode = XDebuggerTreeActionBase.getSelectedNode(e.dataContext)?.valueContainer as? FrontendXValue ?: return
    val project = frontendNode.project
    project.service<FrontendXAddToWatchesTreeActionCoroutineScope>().cs.launch {
      XDebuggerWatchesApi.getInstance().addXValueWatch(frontendNode.xValueDto.id)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

@Service(Service.Level.PROJECT)
private class FrontendXAddToWatchesTreeActionCoroutineScope(project: Project, val cs: CoroutineScope)