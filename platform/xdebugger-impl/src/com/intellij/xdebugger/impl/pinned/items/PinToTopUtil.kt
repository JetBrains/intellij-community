package com.intellij.xdebugger.impl.pinned.items

import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl

fun XValueNodeImpl?.isPinned(pinToTopManager: XDebuggerPinToTopManager): Boolean {
    val container = this?.valueContainer
    if (container is PinToTopMemberValue) {
        val pinned = container.isPinned
        if (pinned != null)
            return pinned

        val pinInfo = this.getPinInfo()
        if (pinInfo != null)
            return pinToTopManager.isPinned(pinInfo)
        return false
    }
    return false
}

fun XValueNodeImpl?.canBePinned(): Boolean {
    val container = this?.valueContainer
    if (container is PinToTopMemberValue) {
        return container.canBePinned() && this.getPinInfo() != null
    }
    return false
}

fun XValueNodeImpl?.getPinInfo() : PinnedItemInfo? {
    val container = this?.valueContainer
    if (container is PinToTopMemberValue) {
        val parentTag = container.customParentTag ?: parentPinToTopValue?.tag
        val memberName = container.customMemberName ?: this?.name
        if (parentTag != null && memberName != null)
            return PinnedItemInfo(parentTag, memberName)
        return null
    }
    return null
}

val XValueNodeImpl?.parentPinToTopValue: PinToTopParentValue? get() {
    return ((this?.parent as? XValueContainerNode<*>)?.valueContainer as? PinToTopParentValue)
}