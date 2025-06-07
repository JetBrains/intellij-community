// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.withId
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class XJumpToSourceActionBase : XDebuggerTreeActionBase() {
  override fun perform(node: XValueNodeImpl, nodeName: String, e: AnActionEvent) {
    val value = node.valueContainer
    val dialog = e.getData(XDebuggerEvaluationDialog.KEY)

    val session = DebuggerUIUtil.getSessionProxy(e) ?: return
    session.coroutineScope.launch(Dispatchers.EDT) {
      val navigated = withId(value, session) { xValueId ->
        navigateToSource(xValueId)
      }
      if (navigated && dialog != null && `is`("debugger.close.dialog.on.navigate")) {
        dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
      }
    }
  }

  /**
   * @return true if the source position is computed and a navigation request is sent, otherwise false
   */
  abstract suspend fun navigateToSource(xValueId: XValueId): Boolean
}
