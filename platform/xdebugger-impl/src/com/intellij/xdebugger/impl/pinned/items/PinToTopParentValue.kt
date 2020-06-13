// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.pinned.items

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PinToTopParentValue : PinToTopValue {

    @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
    @Deprecated("Implement property 'tag' instead", ReplaceWith("tag"))
    @JvmDefault
    fun getTypeName() : String? = null

    /**
     * Tag used as identifier of a parent node. Pinned status is determined by checking of presence a 'tag - member name' pair
     * in [XDebuggerPinToTopManager]
     */
    @Suppress("DEPRECATION")
    @JvmDefault
    val tag: String?
        get() = getTypeName()
}