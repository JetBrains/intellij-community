// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions

import com.intellij.openapi.actionSystem.AnActionEvent
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
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class XJumpToSourceActionBase : XDebuggerTreeActionBase() {
  override fun perform(node: XValueNodeImpl, nodeName: String, e: AnActionEvent) {
    val value = node.valueContainer
    val dialog = e.getData<XDebuggerEvaluationDialog?>(XDebuggerEvaluationDialog.KEY)
    val project = node.tree.project

    project.service<XDebuggerJumpToSourceCoroutineScope>().cs.launch(Dispatchers.EDT) {
      val navigated = navigateToSource(project, value)
      if (navigated && dialog != null && `is`("debugger.close.dialog.on.navigate")) {
        dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
      }
    }
  }

  /**
   * @return true if the source position is computed and a navigation request is sent, otherwise false
   */
  protected abstract suspend fun navigateToSource(project: Project, value: XValue): Boolean

  companion object {
    /**
     * Starts computing [XSourcePosition] by [navigationRequest], when it is calculated, navigates to it.
     * [XSourcePosition] has to be returned to the [XNavigatable] callback, otherwise the suspend function will be stuck.
     */
    @ApiStatus.Internal
    suspend fun navigateByNavigatable(project: Project, navigationRequest: (XNavigatable) -> Unit): Boolean {
      val xSourceDeferred = CompletableDeferred<XSourcePosition?>()
      val navigatable = XNavigatable { sourcePosition ->
        xSourceDeferred.complete(sourcePosition)
      }
      navigationRequest(navigatable)

      val sourcePosition = xSourceDeferred.await() ?: return false
      withContext(Dispatchers.EDT) {
        sourcePosition.createNavigatable(project).navigate(true)
      }
      return true
    }
  }
}

@Service(Service.Level.PROJECT)
private class XDebuggerJumpToSourceCoroutineScope(project: Project, val cs: CoroutineScope)