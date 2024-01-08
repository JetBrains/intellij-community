// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.navigationToolbar

import com.intellij.ide.navigationToolbar.NavBarModel
import com.intellij.ide.navigationToolbar.NavBarPresentation
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase


abstract class AbstractKotlinNavBarTest : KotlinLightCodeInsightFixtureTestCase() {

    // inspired by: com.intellij.ide.navigationToolbar.JavaNavBarTest#assertNavBarModel
    protected fun doTest(testPath: String) {
        val psiFile = myFixture.configureByFile(dataFile().name)
        val model = NavBarModel(myFixture.project)

        model::class.java.getDeclaredMethod("updateModel", DataContext::class.java)
            .apply { isAccessible = true }
            .invoke(model, (myFixture.editor as EditorEx).dataContext)

        val actualItems = (0 until model.size()).map {
            NavBarPresentation.calcPresentableText(model[it], false)
        }
        val expectedItems = InTextDirectivesUtils.findListWithPrefixes(psiFile.text, "// NAV_BAR_ITEMS:")
        assertOrderedEquals(actualItems, expectedItems)
    }
}
