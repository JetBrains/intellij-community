// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.pinned.items

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PinToTopMemberValue : PinToTopValue {
    fun canBePinned() : Boolean

    /**
     * When not null the name will be used as member name in PinToTop instead of node name. May be useful in a case when
     * a value name presentation differs from its real name
     */
    @JvmDefault
    val customMemberName: String?
        get() = null

    /**
     * When not null this tag will be used instead of getting tag from parent node
     */
    @JvmDefault
    val customParentTag: String?
        get() = null

    /**
     * When not null the value will be used as 'pinned' status instead of checking the status inside [XDebuggerPinToTopManager] maps.
     * It may be useful if you want to implement pinning logic inside your values by listening [XDebuggerPinToTopListener]
     */
    @JvmDefault
    val isPinned: Boolean?
        get() = null
}