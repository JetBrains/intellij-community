// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.pinned.items

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface PinToTopParentValue : PinToTopValue {
    /**
     * Tag used as identifier of a parent node. Pinned status is determined by checking of presence a 'tag - member name' pair
     * in [XDebuggerPinToTopManager]
     */
    @Suppress("DEPRECATION")
    val tag: String?
}