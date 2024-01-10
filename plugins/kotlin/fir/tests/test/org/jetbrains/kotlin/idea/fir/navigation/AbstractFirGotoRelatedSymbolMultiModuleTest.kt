// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.navigation


import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.navigation.AbstractKotlinGotoRelatedSymbolMultiModuleTest
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractFirGotoRelatedSymbolMultiModuleTest: AbstractKotlinGotoRelatedSymbolMultiModuleTest() {
    override fun isFirPlugin(): Boolean = true

    override fun tearDown() {
        runAll(
            ThrowableRunnable { project.invalidateCaches() },
            ThrowableRunnable { super.tearDown() }
        )
    }
}