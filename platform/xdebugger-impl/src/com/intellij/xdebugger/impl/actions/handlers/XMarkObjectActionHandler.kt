// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XDebuggerValueMarkupApi
import com.intellij.ui.ComponentUtil
import com.intellij.xdebugger.impl.actions.MarkObjectActionHandler
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.rpc.XValueMarkerDto
import com.intellij.xdebugger.impl.rpc.withId
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.ValueMarkerPresentationDialog
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Component
import javax.swing.JDialog
import javax.swing.JFrame

internal class XMarkObjectActionHandler : MarkObjectActionHandler() {
  override fun perform(project: Project, event: AnActionEvent) {
    val session = DebuggerUIUtil.getSessionProxy(event)
    if (session == null) return

    val markers = session.valueMarkers
    val node = XDebuggerTreeActionBase.getSelectedNode(event.dataContext)
    if (markers == null || node == null) return
    val detachedView = DebuggerUIUtil.isInDetachedTree(event)
    val treeState = XDebuggerTreeState.saveState(node.tree)

    session.coroutineScope.launch(Dispatchers.EDT) {
      if (performMarkObject(event, node, markers, session)) {
        if (detachedView) {
          node.tree.rebuildAndRestore(treeState)
        }
        session.rebuildViews()
      }
    }
  }

  override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
    val markers: XValueMarkers<*, *>? = getValueMarkers(event)
    if (markers == null) return false

    val value = XDebuggerTreeActionBase.getSelectedValue(event.dataContext)
    return value != null && markers.canMarkValue(value)
  }

  override fun isMarked(project: Project, event: AnActionEvent): Boolean {
    val markers: XValueMarkers<*, *>? = getValueMarkers(event)
    if (markers == null) return false

    val value = XDebuggerTreeActionBase.getSelectedValue(event.dataContext)
    return value != null && markers.getMarkup(value) != null
  }

  override fun isHidden(project: Project, event: AnActionEvent): Boolean {
    return getValueMarkers(event) == null
  }
}

private fun getValueMarkers(event: AnActionEvent): XValueMarkers<*, *>? {
  val session = DebuggerUIUtil.getSessionProxy(event)
  return session?.valueMarkers
}

private suspend fun performMarkObject(
  event: AnActionEvent,
  node: XValueNodeImpl,
  markers: XValueMarkers<*, *>,
  proxy: XDebugSessionProxy,
): Boolean {
  val value = node.valueContainer

  val existing = markers.getMarkup(value)
  if (existing != null) {
    withId(value, proxy) { xValueId ->
      XDebuggerValueMarkupApi.getInstance().unmarkValue(xValueId)
    }
  }
  else {
    var component = event.getData<Component?>(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    val window = ComponentUtil.getWindow(component)
    if (window !is JFrame && window !is JDialog) {
      component = window!!.owner
    }
    val dialog = ValueMarkerPresentationDialog(component, node.name, markers.getAllMarkers().values)
    dialog.show()
    val markup = dialog.getConfiguredMarkup()
    if (!dialog.isOK || markup == null) {
      return false
    }
    withId(value, proxy) { xValueId ->
      val marker = XValueMarkerDto(markup.text, markup.color, markup.toolTipText)
      XDebuggerValueMarkupApi.getInstance().markValue(xValueId, marker)
    }
  }
  return true
}
