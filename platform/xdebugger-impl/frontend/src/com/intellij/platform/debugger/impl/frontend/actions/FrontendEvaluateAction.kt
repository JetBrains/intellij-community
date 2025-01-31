// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.actions

import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.FrontendXDebuggerManager
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.xdebugger.impl.actions.areFrontendDebuggerActionsEnabled
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerEvaluateActionHandler
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.xdebugger.impl.rpc.XDebuggerLuxApi
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Frontend version of [com.intellij.xdebugger.impl.actions.EvaluateAction]
 */
private class FrontendEvaluateAction : AnAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun update(e: AnActionEvent) {
    if (!areFrontendDebuggerActionsEnabled()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val evaluator = FrontendXDebuggerManager.getInstance(project).currentSession.value?.evaluator?.value
    if (evaluator == null) {
      e.presentation.isEnabled = false
      e.presentation.isVisible = !e.isFromContextMenu
      return
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val evaluator = FrontendXDebuggerManager.getInstance(project).currentSession.value?.evaluator?.value ?: return

    val focusedDataContext = XDebuggerEvaluateActionHandler.extractFocusedDataContext(e.dataContext) ?: e.dataContext
    val editor = CommonDataKeys.EDITOR.getData(focusedDataContext)
    val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(focusedDataContext)

    val xValue = XDebuggerTreeActionBase.getSelectedNode(focusedDataContext)?.valueContainer as? FrontendXValue

    project.service<FrontendEvaluateActionCoroutineScope>().cs.launch {
      XDebuggerLuxApi.getInstance().showLuxEvaluateDialog(
        evaluator.evaluatorDto.id, editor?.editorId(), virtualFile?.rpcId(), xValue?.xValueDto?.id
      )
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

@Service(Service.Level.PROJECT)
private class FrontendEvaluateActionCoroutineScope(project: Project, val cs: CoroutineScope)