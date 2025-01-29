// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class XJumpToSourceActionBase : XDebuggerTreeActionBase() {
  override fun perform(node: XValueNodeImpl, nodeName: String, e: AnActionEvent) {
    val value = node.valueContainer
    val dialog = e.getData<XDebuggerEvaluationDialog?>(XDebuggerEvaluationDialog.KEY)
    val navigatable = XNavigatable { sourcePosition: XSourcePosition? ->
      if (sourcePosition != null) {
        val project = node.tree.project
        project.service<XDebuggerJumpToSourceCoroutineScope>().cs.launch(Dispatchers.EDT) {
          sourcePosition.createNavigatable(project).navigate(true)
          if (dialog != null && `is`("debugger.close.dialog.on.navigate")) {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
          }
        }
      }
    }
    startComputingSourcePosition(value, navigatable)
  }

  protected abstract fun startComputingSourcePosition(value: XValue, navigatable: XNavigatable)
}

@Service(Service.Level.PROJECT)
private class XDebuggerJumpToSourceCoroutineScope(project: Project, val cs: CoroutineScope)