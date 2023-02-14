// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.navigation

import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.invalidateLibraryCache

abstract class AbstractKotlinGotoImplementationTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        invalidateLibraryCache(project)
    }

    protected fun doTest(path: String) {
        myFixture.configureByFile(path)
        val gotoData = NavigationTestUtils.invokeGotoImplementations(editor, file)
        NavigationTestUtils.assertGotoDataMatching(editor, gotoData)
    }
}