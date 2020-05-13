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
    private var myPinnedMembers = HashMap<String, PinnedItemInfo>()
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
        val pinnedValue = node.valueContainer as? PinToTopMemberValue ?: return
        if ((valueNode.parent as? XValueNodeImpl)?.valueContainer !is PinToTopParentValue) {
            return
        }

        if (!pinnedValue.canBePinned() || isItemPinned(node)) {
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

    fun getPinnedItemInfos() = myPinnedMembers.values.toList()

    fun addItemInfo(typeName: String, fieldName: String) {
        val info = PinnedItemInfo(typeName, fieldName)
        myPinnedMembers[info.getKey()] = info
        for (listener in myListeners) {
            listener.onPinnedItemAdded(info)
        }
    }

    fun removeItemInfo(typeName: String, fieldName: String) {
        val key = PinnedItemInfo.getKey(typeName, fieldName)
        val info = myPinnedMembers[key] ?: return
        myPinnedMembers.remove(key)
        for (listener in myListeners) {
            listener.onPinnedItemRemoved(info)
        }
    }

    fun isItemPinned(node: XValueNodeImpl?) : Boolean {
        val typeName = ((node?.parent as? XValueContainerNode<*>)?.valueContainer as? PinToTopParentValue)?.getTypeName() ?: return false
        return myPinnedMembers.containsKey(
            PinnedItemInfo.getKey(typeName, node.name ?: ""))
    }

    private fun disposeCurrentNodeHoverSubscription() {
        Disposer.dispose(myNodeHoverLifetime ?: return)
        myPinToTopIconAlarm.cancelAllRequests()
    }

    fun saveState(state: PinToTopManagerState) {
        state.pinnedMembersList = myPinnedMembers.toList().map { it.second }.toMutableList()
    }

    fun loadState(state: PinToTopManagerState) {
        myPinnedMembers.putAll(state.pinnedMembersList.map { Pair(it.getKey(), it) })
    }

    fun isPinToTopSupported(node: XDebuggerTreeNode?) : Boolean {
        if (node !is XValueContainerNode<*>) return false
        return node.valueContainer is PinToTopValue
    }
}

