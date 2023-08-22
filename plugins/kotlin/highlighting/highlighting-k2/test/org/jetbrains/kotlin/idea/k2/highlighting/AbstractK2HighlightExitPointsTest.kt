// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.highlighting

import org.jetbrains.kotlin.idea.highlighter.AbstractHighlightExitPointsTest
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil

abstract class AbstractK2HighlightExitPointsTest: AbstractHighlightExitPointsTest() {
    override fun isFirPlugin(): Boolean = true

    override fun doTest(unused: String) {
        ConfigLibraryUtil.configureKotlinRuntimeAndSdk(module, projectDescriptor.sdk!!)
        try {
            super.doTest(unused)
        } finally {
            ConfigLibraryUtil.unConfigureKotlinRuntimeAndSdk(module, projectDescriptor.sdk!!)
        }
    }
}