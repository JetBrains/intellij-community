// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.reveal.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.reveal.RevealMemberValue
import com.intellij.xdebugger.impl.reveal.RevealParentValue
import com.intellij.xdebugger.impl.reveal.XDebuggerRevealManager
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XEvaluationCallbackBase
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import java.awt.event.MouseEvent

class XDebuggerRevealAction : XDebuggerTreeActionBase() {

    companion object {

        const val REVEAL_NAME = "Reveal"
        const val CONCEAL_NAME = "Conceal"

        fun revealField(event: MouseEvent?, node: XValueNodeImpl) {
            ActionManager.getInstance().getAction("XDebugger.Reveal").actionPerformed(
                AnActionEvent.createFromInputEvent(
                    event,
                    XDebuggerRevealAction::class.java.name,
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

        private val logger = Logger.getInstance(XDebuggerRevealAction::class.java)
    }

    override fun update(e: AnActionEvent) {
        val node = getSelectedNode(e.dataContext)
        val valueContainer = node?.valueContainer as? RevealMemberValue
        val presentation = e.presentation
        val project = e.project
        if (valueContainer == null || project == null) {
            presentation.isEnabledAndVisible = false
            return
        }
        val revealManager = XDebuggerRevealManager.getInstance(project)
        if (!revealManager.isRevealSupported(node)) {
            presentation.isEnabledAndVisible = false
            return
        }
        presentation.isVisible = true
        presentation.isEnabled = valueContainer.canBeRevealed()
        presentation.icon = if (revealManager.isItemRevealed(node)) AllIcons.Debugger.Reveal.RevealOff else AllIcons.Debugger.Reveal.RevealOn
        presentation.text = if (revealManager.isItemRevealed(node)) CONCEAL_NAME else REVEAL_NAME

    }

    override fun perform(node: XValueNodeImpl?, nodeName: String, e: AnActionEvent) {
        node ?: return
        val project = e.project ?: return
        val session = XDebuggerManager.getInstance(project).currentSession ?: return
        val nodeValue = node.valueContainer as? RevealMemberValue ?: return
        if (!nodeValue.canBeRevealed()) {
            return
        }
        val parentType = ((node.parent as? XValueNodeImpl)?.valueContainer as? RevealParentValue)?.getTypeName()

        if (parentType.isNullOrEmpty()) {
            return
        }

        if (XDebuggerRevealManager.getInstance(project).isItemRevealed(node)) {
            removePrioritizedItem(parentType, nodeName, project)
        } else {
            addPrioritizedItem(parentType, nodeName, project)
        }

        session.rebuildViews()
        val tree = node.tree
        if (tree.isRootVisible) { //tree is inside evaluation popup
            val oldRoot = tree.root as? XValueNodeImpl ?: return
            val name = oldRoot.name ?: return
            session.debugProcess.evaluator?.evaluate(name, object : XEvaluationCallbackBase() {
                override fun errorOccurred(errorMessage: String) {
                    logger.warn("Failed to update '$name' presentation after '$REVEAL_NAME': $errorMessage")
                }

                override fun evaluated(result: XValue) {
                    tree.setRoot(XValueNodeImpl(tree, null, oldRoot.name, result), true)
                }

            }, tree.sourcePosition)
        }
    }

    private fun addPrioritizedItem(parentType: String, nodeName: String, project: Project) {
        XDebuggerRevealManager.getInstance(project).addItemInfo(parentType, nodeName)
    }

    private fun removePrioritizedItem(parentType: String, nodeName: String, project: Project) {
        XDebuggerRevealManager.getInstance(project).removeItemInfo(parentType, nodeName)
    }
}