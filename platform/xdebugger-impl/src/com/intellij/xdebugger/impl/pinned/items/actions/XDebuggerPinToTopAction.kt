// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.pinned.items.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.pinned.items.PinToTopMemberValue
import com.intellij.xdebugger.impl.pinned.items.PinToTopParentValue
import com.intellij.xdebugger.impl.pinned.items.XDebuggerPinToTopManager
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import icons.PlatformDebuggerImplIcons
import java.awt.event.MouseEvent

class XDebuggerPinToTopAction : XDebuggerTreeActionBase() {

    companion object {

        fun pinToTopField(event: MouseEvent?, node: XValueNodeImpl) {
            ActionManager.getInstance().getAction("XDebugger.PinToTop").actionPerformed(
                AnActionEvent.createFromInputEvent(
                    event,
                    XDebuggerPinToTopAction::class.java.name,
                    Presentation(),
                    object : DataContext {
                        override fun getData(dataId: String): Any? {
                            if (dataId == XDebuggerTree.XDEBUGGER_TREE_KEY.name) {
                                return node.tree
                            }
                            if (dataId == CommonDataKeys.PROJECT.name) {
                                return node.tree.project
                            }
                            return null
                        }

                    }
                )
            )
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
        presentation.isEnabled = (node.parent as? XValueNodeImpl)?.valueContainer is PinToTopParentValue && valueContainer.canBePinned()
        presentation.icon = if (pinToTopManager.isItemPinned(node)) PlatformDebuggerImplIcons.PinToTop.UnpinnedItem else PlatformDebuggerImplIcons.PinToTop.PinnedItem
        presentation.text = if (pinToTopManager.isItemPinned(node)) XDebuggerBundle.message("xdebugger.unpin.action") else XDebuggerBundle.message("xdebugger.pin.to.top.action")

    }

    override fun perform(node: XValueNodeImpl?, nodeName: String, e: AnActionEvent) {
        node ?: return
        val project = e.project ?: return
        val nodeValue = node.valueContainer as? PinToTopMemberValue ?: return
        if (!nodeValue.canBePinned()) {
            return
        }
        val parentType = ((node.parent as? XValueNodeImpl)?.valueContainer as? PinToTopParentValue)?.getTypeName()

        if (parentType.isNullOrEmpty()) {
            return
        }

        if (XDebuggerPinToTopManager.getInstance(project).isItemPinned(node)) {
            removePrioritizedItem(parentType, nodeName, project)
        } else {
            addPrioritizedItem(parentType, nodeName, project)
        }

        XDebuggerUtilImpl.rebuildTreeAndViews(node.tree)
    }

    private fun addPrioritizedItem(parentType: String, nodeName: String, project: Project) {
        XDebuggerPinToTopManager.getInstance(project).addItemInfo(parentType, nodeName)
    }

    private fun removePrioritizedItem(parentType: String, nodeName: String, project: Project) {
        XDebuggerPinToTopManager.getInstance(project).removeItemInfo(parentType, nodeName)
    }
}