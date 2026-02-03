// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.kmp

import org.jetbrains.kotlin.idea.test.KotlinLightMultiplatformCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.configureMultiPlatformModuleStructure

abstract class AbstractK2KmpLightFixtureHighlightingTest : KotlinLightMultiplatformCodeInsightFixtureTestCase() {

    fun doTest(path: String) {
        val allFiles = myFixture.configureMultiPlatformModuleStructure(path).allFiles
        myFixture.testHighlightingAllFiles(true, false, false, *allFiles.toTypedArray())
    }
}
