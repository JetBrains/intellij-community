// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.reveal

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.RevealManagerState
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl

open class XDebuggerRevealManager {

    companion object {
        fun getInstance(project: Project): XDebuggerRevealManager = (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).revealManager

        private const val DEFAULT_ICON_DELAY = 300L
    }

    private val myListeners = mutableListOf<XDebuggerRevealListener>()

    private var myNodeHoverLifetime : Disposable? = null
    private var myActiveNode: XDebuggerTreeNode? = null
    private var myRevealedMembers = HashMap<String, RevealItemInfo>()
    private val myRevealIconAlarm = Alarm()

    val revealComparator : Comparator<XValueNodeImpl> = Comparator.comparing<XValueNodeImpl, Boolean> { !isItemRevealed(it) }
    val compoundComparator = revealComparator.then(XValueNodeImpl.COMPARATOR)

    fun onNodeHovered(node: XDebuggerTreeNode?, lifetimeHolder: Disposable) {
        if (myActiveNode == node) {
            return
        }

        disposeCurrentNodeHoverSubscription()

        if (!isRevealSupported(node)) {
            return
        }

        val valueNode = node as? XValueNodeImpl ?: return
        val revealValue = node.valueContainer as? RevealMemberValue ?: return
        if (!revealValue.canBeRevealed() || isItemRevealed(node)) {
            return
        }
        val valuePresentation = valueNode.valuePresentation ?: return
        val hasChildren = !valueNode.isLeaf
        val oldIcon = valueNode.icon

        val changeIconLifetime = Disposable {
            if (node.icon != oldIcon) {
                node.setPresentation(oldIcon, valuePresentation, hasChildren)
            }
            myActiveNode = null
        }
        myActiveNode = node

        myRevealIconAlarm.addRequest({
            node.setPresentation(AllIcons.Debugger.Reveal.RevealOff, valuePresentation, hasChildren)
        }, DEFAULT_ICON_DELAY)
        myNodeHoverLifetime = changeIconLifetime
        Disposer.register(lifetimeHolder, changeIconLifetime)
    }

    fun addListener(listener: XDebuggerRevealListener, disposable: Disposable) {
        myListeners.add(listener)
        Disposer.register(disposable, Disposable { myListeners.remove(listener) })
    }

    fun removeListener(listener: XDebuggerRevealListener) {
        myListeners.remove(listener)
    }

    @Suppress("unused")
    fun getRevealItemInfos() = myRevealedMembers.values.toList()

    fun addItemInfo(typeName: String, fieldName: String) {
        val info = RevealItemInfo(typeName, fieldName)
        myRevealedMembers[info.getKey()] = info
        for (listener in myListeners) {
            listener.onRevealItemAdded(info)
        }
    }

    fun removeItemInfo(typeName: String, fieldName: String) {
        val key = RevealItemInfo.getKey(typeName, fieldName)
        val info = myRevealedMembers[key] ?: return
        myRevealedMembers.remove(key)
        for (listener in myListeners) {
            listener.onRevealItemRemoved(info)
        }
    }

    fun isItemRevealed(node: XValueNodeImpl?) : Boolean {
        val typeName = ((node?.parent as? XValueContainerNode<*>)?.valueContainer as? RevealParentValue)?.getTypeName() ?: return false
        return myRevealedMembers.containsKey(RevealItemInfo.getKey(typeName, node.name ?: ""))
    }

    private fun disposeCurrentNodeHoverSubscription() {
        Disposer.dispose(myNodeHoverLifetime ?: return)
        myRevealIconAlarm.cancelAllRequests()
    }

    fun saveState(state: RevealManagerState) {
        state.revealedMembersList = myRevealedMembers.toList().map { it.second }.toMutableList()
    }

    fun loadState(state: RevealManagerState) {
        myRevealedMembers.putAll(state.revealedMembersList.map { Pair(it.getKey(), it) })
    }

    fun isRevealSupported(node: XDebuggerTreeNode?) : Boolean {
        if (node !is XValueContainerNode<*>) return false
        return node.valueContainer is RevealValue
    }
}

