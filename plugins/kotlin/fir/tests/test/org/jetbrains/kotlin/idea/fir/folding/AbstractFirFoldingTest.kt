// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.folding

import org.jetbrains.kotlin.idea.folding.AbstractKotlinFoldingTest

abstract class AbstractFirFoldingTest: AbstractKotlinFoldingTest() {
    override fun isFirPlugin(): Boolean {
        return true
    }
}