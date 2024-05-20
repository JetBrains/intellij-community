// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.kmp

import org.jetbrains.kotlin.idea.test.KotlinLightMultiplatformCodeInsightFixtureTestCase

abstract class AbstractK2KmpLightFixtureHighlightingTest : KotlinLightMultiplatformCodeInsightFixtureTestCase() {
    override fun isFirPlugin(): Boolean = true

    fun doTest(path: String) {
        val allFiles = configureModuleStructure(path).allFiles
        myFixture.testHighlightingAllFiles(true, false, false, *allFiles.toTypedArray())
    }
}
