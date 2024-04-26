// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2

import org.jetbrains.kotlin.idea.intentions.declarations.AbstractJoinLinesTest

abstract class AbstractKotlinFirJoinLinesTest: AbstractJoinLinesTest() {
    override fun isFirPlugin(): Boolean {
        return true
    }
}