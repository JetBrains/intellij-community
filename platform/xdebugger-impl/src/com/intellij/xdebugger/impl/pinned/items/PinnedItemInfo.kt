// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.pinned.items

import com.intellij.util.xmlb.annotations.Attribute

data class PinnedItemInfo(
    @Attribute("typeName") var typeName: String,
    @Attribute("fieldName") var fieldName: String) {

    companion object {
        fun getKey(typeName: String, fieldName: String) = "$typeName:$fieldName"
    }

    @Suppress("unused")
    constructor() : this("", "")

    fun getKey() = getKey(typeName, fieldName)
}