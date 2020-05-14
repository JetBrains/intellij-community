// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.pinned.items

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Alarm
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.PinToTopManagerState
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import icons.PlatformDebuggerImplIcons

open class XDebuggerPinToTopManager {

    companion object {
        fun getInstance(project: Project): XDebuggerPinToTopManager = (XDebuggerManager.getInstance(project) as XDebuggerManagerImpl).pinToTopManager

        private const val DEFAULT_ICON_DELAY = 300L
    }

    private val myListeners = mutableListOf<XDebuggerPinToTopListener>()

    private var myNodeHoverLifetime : Disposable? = null
    private var myActiveNode: XDebuggerTreeNode? = null
    private var myPinnedMembers = HashSet<PinnedItemInfo>()
    private val myPinToTopIconAlarm = Alarm()

    val pinToTopComparator : Comparator<XValueNodeImpl> = Comparator.comparing<XValueNodeImpl, Boolean> { !isItemPinned(it) }
    val compoundComparator = pinToTopComparator.then(XValueNodeImpl.COMPARATOR)

    fun isEnabled() : Boolean {
        return Registry.`is`("debugger.field.pin.to.top", true)
    }

    fun onNodeHovered(node: XDebuggerTreeNode?, lifetimeHolder: Disposable) {
        if (myActiveNode == node) {
            return
        }
        if (!isEnabled()) {
            return
        }

        disposeCurrentNodeHoverSubscription()

        if (!isPinToTopSupported(node)) {
            return
        }

        val valueNode = node as? XValueNodeImpl ?: return
        if (!valueNode.canBePinned() || node.isPinned(this)) {
            return
        }
        var oldIcon = valueNode.icon

        val changeIconLifetime = Disposable {
            val xValuePresentation = node.valuePresentation
            if (node.icon == PlatformDebuggerImplIcons.PinToTop.UnpinnedItem && xValuePresentation != null) {
                node.setPresentation(oldIcon, xValuePresentation, !node.isLeaf)
            }
            myActiveNode = null
        }
        myActiveNode = node

        myPinToTopIconAlarm.addRequest({
            val xValuePresentation = node.valuePresentation ?: return@addRequest
            oldIcon = node.icon //update icon with actual value
            node.setPresentation(PlatformDebuggerImplIcons.PinToTop.UnpinnedItem, xValuePresentation, !node.isLeaf)
        }, DEFAULT_ICON_DELAY)
        myNodeHoverLifetime = changeIconLifetime
        Disposer.register(lifetimeHolder, changeIconLifetime)
    }

    fun addListener(listener: XDebuggerPinToTopListener, disposable: Disposable) {
        myListeners.add(listener)
        Disposer.register(disposable, Disposable { myListeners.remove(listener) })
    }

    fun removeListener(listener: XDebuggerPinToTopListener) {
        myListeners.remove(listener)
    }

    fun getPinnedItemInfos() = myPinnedMembers.toList()

    fun addItemInfo(info: PinnedItemInfo) {
        myPinnedMembers.add(info)
        for (listener in myListeners) {
            listener.onPinnedItemAdded(info)
        }
    }

    fun removeItemInfo(info: PinnedItemInfo) {
        myPinnedMembers.remove(info)
        for (listener in myListeners) {
            listener.onPinnedItemRemoved(info)
        }
    }

    fun isItemPinned(node: XValueNodeImpl?) : Boolean = node.isPinned(this)

    fun isPinned(pinnedItemInfo: PinnedItemInfo): Boolean {
        return myPinnedMembers.contains(pinnedItemInfo)
    }

    private fun disposeCurrentNodeHoverSubscription() {
        Disposer.dispose(myNodeHoverLifetime ?: return)
        myPinToTopIconAlarm.cancelAllRequests()
    }

    fun saveState(state: PinToTopManagerState) {
        state.pinnedMembersList = myPinnedMembers.toMutableList()
    }

    fun loadState(state: PinToTopManagerState) {
        myPinnedMembers.addAll(state.pinnedMembersList)
    }

    fun isPinToTopSupported(node: XDebuggerTreeNode?) : Boolean {
        if (node !is XValueContainerNode<*>) return false
        return node.valueContainer is PinToTopValue
    }
}

