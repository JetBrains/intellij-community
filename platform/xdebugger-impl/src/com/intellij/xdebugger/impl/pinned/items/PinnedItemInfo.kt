// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.pinned.items

import com.intellij.util.xmlb.annotations.Attribute

data class PinnedItemInfo(
    @Attribute("parentTag") var parentTag: String,
    @Attribute("memberName") var memberName: String) {

    companion object {
        fun getKey(parentTag: String, memberName: String) = "$parentTag:$memberName"
    }

    @Suppress("unused")
    constructor() : this("", "")

    fun getKey() = getKey(parentTag, memberName)
}