// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.codeInsight

import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.codeMetaInfo.AbstractMultiModuleLineMarkerCodeMetaInfoTest
import org.jetbrains.kotlin.idea.test.runAll

abstract class AbstractK2MultiModuleLineMarkerTest: AbstractMultiModuleLineMarkerCodeMetaInfoTest() {
    override fun isFirPlugin(): Boolean = true

    override fun setUp() {
        super.setUp()
        Registry.get("kotlin.k2.kmp.enabled").setValue(true)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable {
                Registry.get("kotlin.k2.kmp.enabled").setValue(false)
            },
            ThrowableRunnable { super.tearDown() }
        )
    }
}