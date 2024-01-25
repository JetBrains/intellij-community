// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.navigationToolbar

import com.intellij.ide.navbar.tests.contextNavBarPathStrings
import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase


abstract class AbstractKotlinNavBarTest : KotlinLightCodeInsightFixtureTestCase() {

    // inspired by: com.intellij.ide.navigationToolbar.JavaNavBarTest#assertNavBarModel
    protected fun doTest(testPath: String) {
        val psiFile = myFixture.configureByFile(dataFile().name)
        val actualItems = contextNavBarPathStrings((myFixture.editor as EditorEx).dataContext)
        val expectedItems = InTextDirectivesUtils.findListWithPrefixes(psiFile.text, "// NAV_BAR_ITEMS:")
        assertOrderedEquals(actualItems, expectedItems)
    }
}
