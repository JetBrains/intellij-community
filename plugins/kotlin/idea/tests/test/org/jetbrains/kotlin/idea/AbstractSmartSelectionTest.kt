// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils
import org.jetbrains.kotlin.idea.refactoring.getExpressionShortText
import org.jetbrains.kotlin.idea.refactoring.getSmartSelectSuggestions
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractSmartSelectionTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTestSmartSelection(path: String) {
        myFixture.configureByFile(path)

        val expectedResultText = KotlinTestUtils.getLastCommentInFile(getFile() as KtFile)
        val elements = getSmartSelectSuggestions(getFile(), getEditor().caretModel.offset, CodeInsightUtils.ElementKind.EXPRESSION)
        assertEquals(expectedResultText, elements.joinToString(separator = "\n", transform = ::getExpressionShortText))
    }
}
