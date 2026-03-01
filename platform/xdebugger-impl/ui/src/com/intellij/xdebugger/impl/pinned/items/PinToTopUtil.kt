// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.pinned.items

import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting

@Internal
@VisibleForTesting
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

internal fun XValueNodeImpl?.canBePinned(): Boolean {
    val container = this?.valueContainer
    if (container is PinToTopMemberValue) {
        return container.canBePinned() && this.getPinInfo() != null
    }
    return false
}

internal fun XValueNodeImpl?.getPinInfo() : PinnedItemInfo? {
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

internal val XValueNodeImpl?.parentPinToTopValue: PinToTopParentValue? get() {
    return ((this?.parent as? XValueContainerNode<*>)?.valueContainer as? PinToTopParentValue)
}