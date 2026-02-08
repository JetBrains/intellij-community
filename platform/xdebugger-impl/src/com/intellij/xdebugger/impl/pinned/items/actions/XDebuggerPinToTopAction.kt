// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.pinned.items.actions

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsCollectorImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.pinned.items.PinToTopMemberValue
import com.intellij.xdebugger.impl.pinned.items.XDebuggerPinToTopManager
import com.intellij.xdebugger.impl.pinned.items.canBePinned
import com.intellij.xdebugger.impl.pinned.items.getPinInfo
import com.intellij.xdebugger.impl.pinned.items.isPinned
import com.intellij.xdebugger.impl.pinned.items.parentPinToTopValue
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeSplitActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import icons.PlatformDebuggerImplIcons
import java.awt.event.MouseEvent
import java.util.Collections

class XDebuggerPinToTopAction : XDebuggerTreeSplitActionBase() {

    companion object {
        fun pinToTopField(mouseEvent: MouseEvent?, node: XValueNodeImpl) {
          val event = AnActionEvent.createFromInputEvent(
            mouseEvent,
            XDebuggerPinToTopAction::class.java.name,
            Presentation(),
            SimpleDataContext.builder()
              .add(XDebuggerTree.XDEBUGGER_TREE_KEY, node.tree)
              .add(CommonDataKeys.PROJECT, node.tree.project)
              .add(XDebuggerTree.SELECTED_NODES, Collections.singletonList(node))
              .build()
          )
          if (node.name != null) { // to follow the "perform" logic
            performImpl(node.tree.project, node)
            val action = ActionManager.getInstance().getAction("XDebugger.PinToTop")
            ActionsCollectorImpl.onAfterActionInvoked(action, event, AnActionResult.PERFORMED)
          }
        }
    }

    override fun update(e: AnActionEvent) {
        val node = getSelectedNode(e.dataContext)
        val valueContainer = node?.valueContainer as? PinToTopMemberValue
        val presentation = e.presentation
        val project = e.project
        if (valueContainer == null || project == null) {
            presentation.isEnabledAndVisible = false
            return
        }
        val pinToTopManager = XDebuggerPinToTopManager.getInstance(project)
        if (!pinToTopManager.isPinToTopSupported(node)) {
            presentation.isEnabledAndVisible = false
            return
        }
        presentation.isVisible = true
        presentation.isEnabled = node.canBePinned()
        presentation.icon = if (pinToTopManager.isItemPinned(node)) PlatformDebuggerImplIcons.PinToTop.UnpinnedItem else PlatformDebuggerImplIcons.PinToTop.PinnedItem
        presentation.text = if (pinToTopManager.isItemPinned(node)) XDebuggerBundle.message("xdebugger.unpin.action") else XDebuggerBundle.message("xdebugger.pin.to.top.action")

    }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun perform(node: XValueNodeImpl, nodeName: String, e: AnActionEvent) {
    val project = e.project ?: return
    performImpl(project, node)
  }
}

private fun performImpl(project: Project, node: XValueNodeImpl) {
  if (!node.canBePinned())
    return

  val pinToTopManager = XDebuggerPinToTopManager.getInstance(project)

  val pinInfo = node.getPinInfo()
                ?: return

  val pinNode = !node.isPinned(pinToTopManager)
  if (pinNode) {
    pinToTopManager.addItemInfo(pinInfo)
  }
  else {
    pinToTopManager.removeItemInfo(pinInfo)
  }
  node.parentPinToTopValue?.onChildPinned(pinNode, pinInfo)

  DebuggerUIUtil.rebuildTreeAndViews(node.tree)
}
