// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.ui.SplitDebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.annotations.ApiStatus

/**
 * Base class for backend-only actions that operate on [XValue] instances.
 *
 * This action operates with [XValue]s provided by the backend.
 *
 * Supported UI: [com.intellij.openapi.ui.DialogWrapper.show]
 *
 * Use this base class for backend-only actions, which do not operate on the frontend.
 * If you need to create an action which operates on the frontend, use [XDebuggerTreeSplitActionBase] instead.
 */
@ApiStatus.Experimental
abstract class XDebuggerTreeBackendOnlyActionBase : AnAction(), ActionRemoteBehaviorSpecification.BackendOnly {

  override fun update(e: AnActionEvent) {
    val selectedValue = getSelectedValueWithName(e.dataContext)
    e.presentation.setEnabled(selectedValue != null && isEnabled(selectedValue.xValue, selectedValue.name, e) )
  }

  protected open fun isEnabled(value: XValue, nodeName: @NlsSafe String?, e: AnActionEvent): Boolean {
    return nodeName != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val (xValue, name) = getSelectedValueWithName(e.dataContext) ?: return
    if (name != null) {
      perform(xValue, name, e)
    }
  }

  protected abstract fun perform(value: XValue, nodeName: @NlsSafe String, e: AnActionEvent)

  companion object {
    @JvmStatic
    fun getSelectedValues(dataContext: DataContext): List<XValue> {
      return getSelectedValuesWithNames(dataContext).map { it.xValue }
    }

    @JvmStatic
    fun getSelectedValue(dataContext: DataContext): XValue? {
      return getSelectedValues(dataContext).firstOrNull()
    }

    private fun getSelectedValuesWithNames(dataContext: DataContext): List<XDebuggerTreeSelectedValue> {
      return SplitDebuggerUIUtil.getXDebuggerTreeSelectedBackendValues(dataContext)
    }

    private fun getSelectedValueWithName(dataContext: DataContext): XDebuggerTreeSelectedValue? =
      getSelectedValuesWithNames(dataContext).firstOrNull()
  }
}

@ApiStatus.Internal
data class XDebuggerTreeSelectedValue(val xValue: XValue, val name: String?, val node: XValueNodeImpl?)
